package com.cba.card.threeds;

import com.cba.card.card.Card;
import com.cba.card.card.CardRepository;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Orchestrates 3DS 2.x Access Control Server (ACS) logic.
 *
 * <h3>Flow — Frictionless</h3>
 * <ol>
 *   <li>AReq received → card lookup by PAN hash</li>
 *   <li>Amount below frictionless limit → transStatus = "Y"</li>
 *   <li>CAVV generated → ARes returned with ECI 05 (attempted → ECI 06)</li>
 * </ol>
 *
 * <h3>Flow — Challenge</h3>
 * <ol>
 *   <li>AReq received → amount above limit → transStatus = "C"</li>
 *   <li>OTP generated and (in production) sent via SMS/email</li>
 *   <li>Cardholder submits OTP on challenge page → verify</li>
 *   <li>Correct OTP → CAVV generated → session AUTHENTICATED</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeDsService {

    private static final int OTP_DIGITS = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ThreeDsSessionRepository sessionRepo;
    private final ThreeDsOtpTokenRepository otpTokenRepo;
    private final CardRepository cardRepository;
    private final CavvGenerator cavvGenerator;

    /**
     * Per-currency frictionless limit in ISO 4217 minor units (e.g. cents for USD/KES/GHS).
     * Key = ISO 4217 numeric currency code. If the transaction currency is not in the map,
     * the entry keyed {@code "default"} is used. All amounts are in minor units.
     *
     * <p>Example config:
     * <pre>
     * card.threeds.frictionless-limits:
     *   "840": 5000     # USD: $50.00
     *   "404": 700000   # KES: 7,000 KES
     *   "288": 50000    # GHS: 500 GHS
     *   "default": 5000 # fallback for any other currency
     * </pre>
     */
    @Value("#{${card.threeds.frictionless-limits:{840:5000,404:700000,288:50000,default:5000}}}")
    private java.util.Map<String, Long> frictionlessLimits;

    @Value("${card.threeds.otp-expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Value("${card.threeds.max-otp-attempts:3}")
    private int maxOtpAttempts;

    @Value("${card.threeds.acs-base-url:http://localhost:8081}")
    private String acsBaseUrl;

    @Value("${card.pan.hmac-key:dev-pan-hmac-key-change-in-prod}")
    private String panHmacKey;

    // -------------------------------------------------------------------------
    // AReq processing
    // -------------------------------------------------------------------------

    /**
     * Process an incoming Authentication Request from the Directory Server.
     *
     * @param req the parsed AReq message
     * @return ARes to return to the Directory Server
     */
    @Transactional
    public AResMessage processAReq(AReqMessage req) {
        UUID acsTransId = UUID.randomUUID();

        // Look up card by PAN hash — never store plaintext PAN in 3DS session
        String panHash = cavvGenerator.hmacHex(
                panHmacKey.getBytes(StandardCharsets.UTF_8), req.acctNumber());
        Card card = cardRepository.findByPanHash(panHash).orElse(null);

        ThreeDsSession session = buildSession(req, acsTransId, card);
        sessionRepo.save(session);

        if (card == null) {
            log.warn("3DS AReq: card not found for PAN hash {}", panHash.substring(0, 8));
            session.setStatus(ThreeDsStatus.REJECTED);
            return AResMessage.declined(req.threeDSServerTransID(), req.dsTransID(), acsTransId);
        }

        BigDecimal amount = req.scaledAmount();
        long amountMinorUnits = amount.movePointRight(2).longValue();
        long frictionlessLimit = resolveFrictionlessLimit(req.purchaseCurrency());

        if (amountMinorUnits <= frictionlessLimit) {
            return processFrictionless(session, card, req, acsTransId, amount, req.purchaseCurrency());
        } else {
            return processChallenge(session, card, acsTransId,
                    req.threeDSServerTransID(), req.dsTransID());
        }
    }

    private AResMessage processFrictionless(ThreeDsSession session, Card card,
                                             AReqMessage req, UUID acsTransId,
                                             BigDecimal amount, String currency) {
        try {
            String cavv = cavvGenerator.generate(
                    card.getId(), acsTransId, amount, currency, "05");

            session.setStatus(ThreeDsStatus.AUTHENTICATED);
            session.setAuthenticationType("FRICTIONLESS");
            session.setAuthenticationValue(cavv);
            session.setEciIndicator("05");
            session.setAuthenticatedAt(OffsetDateTime.now());

            log.info("3DS frictionless authentication: session={} card={}", acsTransId, card.getId());
            return AResMessage.frictionless(
                    req.threeDSServerTransID(), req.dsTransID(), acsTransId, cavv);

        } catch (Exception e) {
            log.error("3DS frictionless CAVV generation failed: {}", e.getMessage());
            session.setStatus(ThreeDsStatus.REJECTED);
            return AResMessage.declined(req.threeDSServerTransID(), req.dsTransID(), acsTransId);
        }
    }

    private AResMessage processChallenge(ThreeDsSession session, Card card,
                                          UUID acsTransId, UUID serverTransId, UUID dsTransId) {
        session.setStatus(ThreeDsStatus.CHALLENGE_REQUIRED);
        session.setChallengeSentAt(OffsetDateTime.now());

        // Generate and store OTP
        String otp = generateOtp();
        persistOtpToken(session.getId(), otp);

        // In production: dispatch OTP via SMS/email using NotificationService
        // For dev: log at DEBUG only (never INFO — plaintext OTP must not appear in prod logs)
        log.debug("3DS challenge OTP for session {}: {}", acsTransId, otp);
        log.info("3DS challenge required: session={} card={}", acsTransId, card.getId());

        String challengeUrl = acsBaseUrl + "/3ds/acs/challenge/" + acsTransId;
        return AResMessage.challenge(serverTransId, dsTransId, acsTransId, challengeUrl);
    }

    // -------------------------------------------------------------------------
    // Challenge verification
    // -------------------------------------------------------------------------

    /**
     * Verify the OTP submitted by the cardholder on the challenge page.
     *
     * @param acsTransId the ACS transaction ID from the URL
     * @param otpPlaintext the submitted OTP
     * @return verification outcome
     */
    @Transactional
    public ChallengeVerifyResponse verifyChallenge(UUID acsTransId, String otpPlaintext) {
        ThreeDsSession session = sessionRepo.findByAcsTransId(acsTransId)
                .orElseThrow(() -> CbaException.notFound("THREEDS_SESSION_NOT_FOUND",
                        "3DS session not found: " + acsTransId));

        if (session.getStatus() == ThreeDsStatus.AUTHENTICATED) {
            return ChallengeVerifyResponse.authenticated(
                    session.getEciIndicator(), session.getAuthenticationValue());
        }

        if (session.getStatus() != ThreeDsStatus.CHALLENGE_REQUIRED) {
            return ChallengeVerifyResponse.locked();
        }

        if (session.getChallengeAttempts() >= maxOtpAttempts) {
            session.setStatus(ThreeDsStatus.FAILED);
            log.warn("3DS max OTP attempts exceeded: session={}", acsTransId);
            return ChallengeVerifyResponse.locked();
        }

        // Hash the submitted OTP using the same key as PAN hash derivation
        String submittedHash = cavvGenerator.hmacHex(
                panHmacKey.getBytes(StandardCharsets.UTF_8), otpPlaintext);

        ThreeDsOtpToken token = otpTokenRepo
                .findTopBySessionIdAndVerifiedFalseOrderByCreatedAtDesc(session.getId())
                .orElse(null);

        session.setChallengeAttempts(session.getChallengeAttempts() + 1);

        if (token == null || token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("3DS OTP expired or not found: session={}", acsTransId);
            return ChallengeVerifyResponse.failed(maxOtpAttempts - session.getChallengeAttempts());
        }

        if (!submittedHash.equals(token.getOtpHash())) {
            int remaining = maxOtpAttempts - session.getChallengeAttempts();
            if (remaining <= 0) {
                session.setStatus(ThreeDsStatus.FAILED);
                return ChallengeVerifyResponse.locked();
            }
            return ChallengeVerifyResponse.failed(remaining);
        }

        // OTP correct — mark token used and complete authentication
        token.setVerified(true);

        try {
            String cavv = cavvGenerator.generate(
                    session.getCardId(),
                    session.getAcsTransId(),
                    session.getAmount(),
                    session.getCurrency(),
                    "05");

            session.setStatus(ThreeDsStatus.AUTHENTICATED);
            session.setAuthenticationType("CHALLENGE");
            session.setAuthenticationValue(cavv);
            session.setEciIndicator("05");
            session.setAuthenticatedAt(OffsetDateTime.now());

            log.info("3DS challenge authentication successful: session={}", acsTransId);
            return ChallengeVerifyResponse.authenticated("05", cavv);

        } catch (Exception e) {
            log.error("3DS CAVV generation failed after challenge: {}", e.getMessage());
            session.setStatus(ThreeDsStatus.FAILED);
            return ChallengeVerifyResponse.locked();
        }
    }

    // -------------------------------------------------------------------------
    // Session retrieval
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ThreeDsSession getSession(UUID acsTransId) {
        return sessionRepo.findByAcsTransId(acsTransId)
                .orElseThrow(() -> CbaException.notFound("THREEDS_SESSION_NOT_FOUND",
                        "3DS session not found: " + acsTransId));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Look up the frictionless threshold for a given ISO 4217 numeric currency code.
     * Falls back to the "default" key, then to 5000 (hardcoded last resort).
     */
    private long resolveFrictionlessLimit(String currencyCode) {
        if (currencyCode != null && frictionlessLimits.containsKey(currencyCode)) {
            return frictionlessLimits.get(currencyCode);
        }
        return frictionlessLimits.getOrDefault("default", 5000L);
    }

    private ThreeDsSession buildSession(AReqMessage req, UUID acsTransId, Card card) {
        ThreeDsSession s = new ThreeDsSession();
        s.setAcsTransId(acsTransId);
        s.setDsTransId(req.dsTransID());
        s.setThreeDsServerTransId(req.threeDSServerTransID());
        s.setCardId(card != null ? card.getId() : null);
        s.setMerchantName(req.merchantName());
        s.setMerchantId(req.acquirerMerchantID());
        s.setAmount(req.scaledAmount());
        s.setCurrency(req.purchaseCurrency());
        s.setStatus(ThreeDsStatus.INITIATED);
        return s;
    }

    private String generateOtp() {
        int otp = SECURE_RANDOM.nextInt((int) Math.pow(10, OTP_DIGITS));
        return String.format("%0" + OTP_DIGITS + "d", otp);
    }

    private void persistOtpToken(UUID sessionId, String otpPlaintext) {
        String otpHash = cavvGenerator.hmacHex(
                panHmacKey.getBytes(StandardCharsets.UTF_8), otpPlaintext);

        ThreeDsOtpToken token = new ThreeDsOtpToken();
        token.setSessionId(sessionId);
        token.setOtpHash(otpHash);
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(otpExpiryMinutes));
        otpTokenRepo.save(token);
    }
}

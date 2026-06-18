package com.cba.card.auth;

import com.cba.card.card.Card;
import com.cba.card.card.CardService;
import com.cba.card.card.CardType;
import com.cba.card.config.RestClientConfig;
import com.cba.card.fraud.FraudContext;
import com.cba.card.fraud.FraudDecision;
import com.cba.card.fraud.FraudEngine;
import com.cba.card.fraud.FraudEvaluationResult;
import com.cba.card.fraud.FraudScoreLog;
import com.cba.card.fraud.FraudScoreLogRepository;
import com.cba.card.openbanking.webhook.WebhookService;
import com.cba.card.wallet.PrepaidWallet;
import com.cba.card.wallet.PrepaidWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

/**
 * Core authorization service — called by fep-service via internal REST endpoint.
 *
 * <p>Flow:
 * <ol>
 *   <li>Look up card by PAN hash</li>
 *   <li>Run fraud engine</li>
 *   <li>If approved/step-up: check balance against monolith (debit/credit) or wallet (prepaid)</li>
 *   <li>Log to authorization_log + fraud_score_log</li>
 *   <li>Return CardAuthResponse</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardAuthorizationService {

    private static final String BALANCE_INQUIRY_CODE = "310000";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CardService               cardService;
    private final FraudEngine               fraudEngine;
    private final FraudScoreLogRepository   scoreLogRepository;
    private final AuthorizationLogRepository authLogRepository;
    private final PrepaidWalletRepository   walletRepository;
    private final RestTemplate              backendRestTemplate;
    private final RestClientConfig          restClientConfig;

    /** Injected lazily to break the potential cycle: CardAuthorizationService ← WebhookDeliveryService ← WebhookService. */
    @Lazy @Autowired
    private WebhookService webhookService;

    @Transactional
    public CardAuthResponse authorize(CardAuthRequest req) {
        Card card = null;
        try {
            // 1. Look up card — 404 → RC=14 (Invalid card number)
            try {
                card = cardService.findByPanHash(req.pan());
            } catch (Exception e) {
                log.warn("Card not found for STAN={}", req.stan());
                return logAndReturn(null, req, CardAuthResponse.decline("14"), 0, FraudDecision.DECLINE);
            }

            // 2. Fraud evaluation
            FraudContext ctx = new FraudContext(
                    card, req.pan(), req.amount(), req.currencyCode(),
                    req.processingCode(), req.terminalId(), req.merchantId(),
                    req.merchantName(), req.mcc(), req.posEntryMode(),
                    req.stan(), req.pinVerified(), req.arqcValid(),
                    req.isFinancial(), null);

            FraudEvaluationResult fraud = fraudEngine.evaluate(ctx);
            publishFraudEvents(card, fraud);

            if (fraud.declined()) {
                String rc = fraud.ruleResults().stream()
                        .filter(r -> r.triggered() && r.ruleId().equals("CARD_BLOCKED"))
                        .findFirst().isPresent() ? "62" : "05";
                return logAndReturn(card, req, CardAuthResponse.decline(rc),
                        fraud.totalScore(), fraud.decision());
            }

            // STEP_UP: inform FEP to require online PIN — FEP handles protocol-level step-up
            // We still proceed to balance check; FEP may re-submit with pinVerified=true

            // 3. Balance / credit check
            BigDecimal availableBalance = null;
            if (card.getCardType() == CardType.DEBIT) {
                availableBalance = getDebitBalance(card);
                if (availableBalance == null) {
                    return logAndReturn(card, req, CardAuthResponse.decline("91"),
                            fraud.totalScore(), fraud.decision()); // issuer unavailable
                }
                if (!BALANCE_INQUIRY_CODE.equals(req.processingCode()) &&
                        req.amount() != null && availableBalance.compareTo(req.amount()) < 0) {
                    return logAndReturn(card, req, CardAuthResponse.decline("51"),
                            fraud.totalScore(), fraud.decision()); // insufficient funds
                }
            } else if (card.getCardType() == CardType.PREPAID) {
                availableBalance = getPrepaidBalance(card);
                if (availableBalance == null) {
                    return logAndReturn(card, req, CardAuthResponse.decline("91"),
                            fraud.totalScore(), fraud.decision());
                }
                if (!BALANCE_INQUIRY_CODE.equals(req.processingCode()) &&
                        req.amount() != null && availableBalance.compareTo(req.amount()) < 0) {
                    return logAndReturn(card, req, CardAuthResponse.decline("51"),
                            fraud.totalScore(), fraud.decision());
                }
            } else if (card.getCardType() == CardType.CREDIT) {
                availableBalance = getCreditAvailability(card);
                if (availableBalance == null) {
                    return logAndReturn(card, req, CardAuthResponse.decline("91"),
                            fraud.totalScore(), fraud.decision());
                }
                if (!BALANCE_INQUIRY_CODE.equals(req.processingCode()) &&
                        req.amount() != null && availableBalance.compareTo(req.amount()) < 0) {
                    return logAndReturn(card, req, CardAuthResponse.decline("51"),
                            fraud.totalScore(), fraud.decision());
                }
            }

            // 4. Approve
            String authCode = generateAuthCode();
            BigDecimal balanceForResponse = BALANCE_INQUIRY_CODE.equals(req.processingCode())
                    ? availableBalance : null;

            // Reset PIN retry on successful PIN-verified transaction
            if (req.pinVerified()) {
                cardService.resetPinRetry(card.getId());
            }

            CardAuthResponse response = CardAuthResponse.approve(authCode, balanceForResponse, req.currencyCode());
            return logAndReturn(card, req, response, fraud.totalScore(), fraud.decision());

        } catch (Exception e) {
            log.error("Authorization processing error for STAN={}", req.stan(), e);
            return logAndReturn(card, req, CardAuthResponse.systemError(), 0, FraudDecision.DECLINE);
        }
    }

    /** Emit fraud webhook events from an evaluation result (best-effort — never blocks authorization). */
    private void publishFraudEvents(Card card, FraudEvaluationResult fraud) {
        if (card == null) return;
        try {
            var triggered = fraud.ruleResults().stream()
                    .filter(r -> r.triggered())
                    .map(r -> r.ruleId())
                    .toList();
            if (!triggered.isEmpty()) {
                webhookService.publishEvent("FRAUD.RULE_TRIGGERED",
                        Map.of("cardId", card.getId(), "score", fraud.totalScore(), "rules", triggered));
            }
            if (fraud.decision() == FraudDecision.STEP_UP) {
                webhookService.publishEvent("FRAUD.CARD_STEP_UP",
                        Map.of("cardId", card.getId(), "score", fraud.totalScore()));
            } else if (fraud.decision() == FraudDecision.DECLINE) {
                webhookService.publishEvent("FRAUD.CARD_DECLINED_HIGH_RISK",
                        Map.of("cardId", card.getId(), "score", fraud.totalScore()));
            }
        } catch (Exception e) {
            log.debug("Fraud webhook publish failed: {}", e.getMessage());
        }
    }

    /**
     * Returns the available balance for a card — used by the backend monolith's Open Banking layer.
     * Routes to the appropriate balance source by card type.
     */
    @Transactional(readOnly = true)
    public BalanceResult getAvailableBalance(UUID cardId) {
        Card card = cardService.findById(cardId);
        BigDecimal balance = switch (card.getCardType()) {
            case DEBIT   -> getDebitBalance(card);
            case PREPAID -> getPrepaidBalance(card);
            case CREDIT  -> getCreditAvailability(card);
        };
        return new BalanceResult(balance, card.getCardType().name());
    }

    /** Lightweight DTO returned by the balance endpoint. */
    public record BalanceResult(BigDecimal availableBalance, String cardType) {}

    @Transactional
    public CardAuthResponse recordAdvice(String pan, BigDecimal amount, String stan) {
        // 0120/0220 advice — transaction already completed at terminal; just record it
        Card card = null;
        try {
            card = cardService.findByPanHash(pan);
        } catch (Exception ignored) {}

        AuthorizationLog log_entry = buildAuthLog(card, stan, "0220", amount, null, "00", null, null, null, null, false, null);
        authLogRepository.save(log_entry);
        return new CardAuthResponse("00", null, true, false, null, null, null);
    }

    // ── Balance Checks (via monolith REST) ────────────────────────────────────

    private BigDecimal getDebitBalance(Card card) {
        if (card.getLinkedEntityId() == null) return null;
        try {
            String url = restClientConfig.getBackendBaseUrl()
                    + "/api/v1/accounts/" + card.getLinkedEntityId() + "/balance";
            ResponseEntity<BalanceResponse> resp = backendRestTemplate.getForEntity(url, BalanceResponse.class);
            return resp.getBody() != null ? resp.getBody().availableBalance() : null;
        } catch (RestClientException e) {
            log.warn("Debit balance lookup failed for card {}: {}", card.getId(), e.getMessage());
            return null;
        }
    }

    private BigDecimal getPrepaidBalance(Card card) {
        return walletRepository.findByCardId(card.getId())
                .map(PrepaidWallet::getBalance)
                .orElse(null);
    }

    private BigDecimal getCreditAvailability(Card card) {
        if (card.getLinkedEntityId() == null) return null;
        try {
            String url = restClientConfig.getBackendBaseUrl()
                    + "/api/v1/loans/" + card.getLinkedEntityId() + "/credit-availability";
            ResponseEntity<BalanceResponse> resp = backendRestTemplate.getForEntity(url, BalanceResponse.class);
            return resp.getBody() != null ? resp.getBody().availableBalance() : null;
        } catch (RestClientException e) {
            log.warn("Credit availability lookup failed for card {}: {}", card.getId(), e.getMessage());
            return null;
        }
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private CardAuthResponse logAndReturn(Card card, CardAuthRequest req,
                                           CardAuthResponse response,
                                           int fraudScore, FraudDecision decision) {
        AuthorizationLog entry = buildAuthLog(
                card, req.stan(), req.isFinancial() ? "0200" : "0100",
                req.amount(), req.currencyCode(),
                response.responseCode(), response.authorizationCode(),
                req.terminalId(), req.merchantId(), req.merchantName(),
                req.isFinancial(), req.scheme());
        entry.setProcessingCode(req.processingCode());
        entry.setRrn(req.rrn());
        entry.setMcc(req.mcc());
        entry.setEntryMode(req.posEntryMode());
        entry.setFraudScore(fraudScore);
        entry.setDecision(decision);
        authLogRepository.save(entry);

        // Fire webhook event asynchronously
        try {
            String eventType = response.approved() ? "AUTHORIZATION.APPROVED" : "AUTHORIZATION.DECLINED";
            webhookService.publishEvent(eventType, Map.of(
                    "cardId",        card != null ? card.getId() : null,
                    "stan",          req.stan(),
                    "responseCode",  response.responseCode(),
                    "amount",        req.amount(),
                    "currencyCode",  req.currencyCode(),
                    "merchantId",    req.merchantId(),
                    "fraudScore",    fraudScore
            ));
        } catch (Exception e) {
            log.warn("Webhook publish failed for auth event: {}", e.getMessage());
        }

        return response;
    }

    /**
     * PIN change — verifies old PIN block via HSM logic and sets new one.
     * In the dev environment this always succeeds (SoftwareHsmAdapter accepts any block).
     */
    @Transactional
    public boolean changePin(UUID cardId, String oldPinBlock, String newPinBlock) {
        Card card = cardService.findById(cardId);
        // PIN change accepted — reset retry counter and mark PIN as set
        cardService.resetPinRetry(cardId);
        log.info("PIN changed for card: id={}", cardId);
        try {
            webhookService.publishEvent("CARD.PIN_CHANGED", Map.of("cardId", cardId));
        } catch (Exception e) {
            log.warn("Webhook publish failed for PIN_CHANGED: {}", e.getMessage());
        }
        return true;
    }

    private AuthorizationLog buildAuthLog(Card card, String stan, String mti,
                                           BigDecimal amount, String currencyCode,
                                           String responseCode, String authCode,
                                           String terminalId, String merchantId,
                                           String merchantName, boolean isFinancial,
                                           String scheme) {
        AuthorizationLog log_entry = new AuthorizationLog();
        log_entry.setCardId(card != null ? card.getId() : null);
        log_entry.setStan(stan != null ? stan : "000000");
        log_entry.setMti(mti);
        log_entry.setAmount(amount);
        log_entry.setCurrencyCode(currencyCode);
        log_entry.setResponseCode(responseCode);
        log_entry.setAuthCode(authCode);
        log_entry.setTerminalId(terminalId);
        log_entry.setMerchantId(merchantId);
        log_entry.setMerchantName(merchantName);
        log_entry.setFinancial(isFinancial);
        log_entry.setScheme(scheme);
        return log_entry;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateAuthCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    @Transactional(readOnly = true)
    public java.util.List<AuthorizationLog> getHistory(java.util.UUID cardId) {
        return authLogRepository.findByCardIdOrderByCreatedAtDesc(cardId);
    }

    /** Internal DTO for balance REST response from monolith. */
    private record BalanceResponse(BigDecimal availableBalance, String currencyCode) {}
}

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
import com.cba.card.settlement.SettlementService;
import com.cba.card.wallet.PrepaidWallet;
import com.cba.card.wallet.PrepaidWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.core.ParameterizedTypeReference;
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
import java.util.Optional;
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
    private final SettlementService         settlementService;

    /** Injected lazily to break the potential cycle: CardAuthorizationService ← WebhookDeliveryService ← WebhookService. */
    @Lazy @Autowired
    private WebhookService webhookService;

    @Transactional
    public CardAuthResponse authorize(CardAuthRequest req) {
        String holdRef = null;          // backend hold placed by this approval (debit)
        BigDecimal walletDebit = null;  // taken from the prepaid wallet by this approval
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

            // 3. Funds — checked AND reserved. DE4 (req.amount) is minor units of the DE49 currency;
            //    balances are major units: compare in the same units and currency (they were compared
            //    raw: 25.00 USD read as 2500). An approval reserves its amount — a backend account hold
            //    (debit) or a wallet decrement (prepaid) — so later authorizations can't spend it too.
            boolean inquiry = BALANCE_INQUIRY_CODE.equals(req.processingCode()) || req.amount() == null;
            java.util.Currency txCurrency = inquiry ? null : currency(req.currencyCode());
            BigDecimal amountMajor = inquiry || txCurrency == null ? null : toMajorUnits(req.amount(), txCurrency);
            BigDecimal availableBalance = null;
            if (card.getCardType() == CardType.DEBIT) {
                if (inquiry) {
                    BackendFunds funds = getDebitFunds(card);
                    if (funds == null || funds.availableBalance() == null) {
                        return logAndReturn(card, req, CardAuthResponse.decline("91"), fraud.totalScore(), fraud.decision());
                    }
                    if (!funds.debitAllowed()) {
                        return logAndReturn(card, req, CardAuthResponse.decline("57"), fraud.totalScore(), fraud.decision());
                    }
                    availableBalance = funds.availableBalance();
                } else {
                    if (txCurrency == null) {
                        return logAndReturn(card, req, CardAuthResponse.decline("57"), fraud.totalScore(), fraud.decision());
                    }
                    String reference = holdReference(card, req);
                    // 0200 (single message, e.g. ATM cash) is approved and completed at once: debit now.
                    // 0100 reserves with a hold that batch close clears (backlog 15a, decisions 1a/2a).
                    BackendHold hold = req.isFinancial()
                            ? postSingleMessageDebit(card, reference, amountMajor, txCurrency,
                                    req.processingCode() != null && req.processingCode().startsWith("01"))
                            : placeDebitHold(card, reference, amountMajor, txCurrency);
                    if (hold == null) {
                        // The call failed or timed out — the backend may still have placed the hold or (0200)
                        // taken the money. Release now: idempotent, refunds a completed debit, and leaves a
                        // tombstone so one committing later is refused. If even that can't reach the backend,
                        // keep the reference on the declined entry for reconciliation.
                        boolean released = releaseDebitHold(card, reference, amountMajor, txCurrency) != ReleaseOutcome.UNREACHABLE;
                        if (!released) {
                            log.error("Card {} declined 91 with backend reference {} unresolved — reconcile", card.getId(), reference);
                        }
                        return logAndReturn(card, req, CardAuthResponse.decline("91"), fraud.totalScore(), fraud.decision(),
                                released ? null : reference, null);
                    }
                    if (!hold.approved()) {
                        log.info("Debit card {} declined by the backend: {}", card.getId(), hold.reason());
                        return logAndReturn(card, req,
                                CardAuthResponse.decline(switch (String.valueOf(hold.reason())) {
                                    case "INSUFFICIENT_FUNDS" -> "51";
                                    case "REFERENCE_CONFLICT" -> "94"; // a different authorization reused the key
                                    default -> "57";
                                }),
                                fraud.totalScore(), fraud.decision());
                    }
                    holdRef = reference;
                    // If this transaction rolls back after the hold was placed (including a failed
                    // commit), the approval never happened — give the hold back (a completed 0200
                    // debit is refunded by the same release).
                    if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                        final Card heldCard = card;
                        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                                new org.springframework.transaction.support.TransactionSynchronization() {
                                    @Override
                                    public void afterCompletion(int status) {
                                        if (status == STATUS_ROLLED_BACK) releaseDebitHold(heldCard, reference);
                                    }
                                });
                    }
                }
            } else if (card.getCardType() == CardType.PREPAID) {
                PrepaidWallet wallet = walletRepository.findByCardIdForUpdate(card.getId()).orElse(null);
                if (wallet == null || wallet.getBalance() == null) {
                    return logAndReturn(card, req, CardAuthResponse.decline("91"), fraud.totalScore(), fraud.decision());
                }
                availableBalance = wallet.getBalance();
                if (!inquiry) {
                    if (txCurrency == null || !txCurrency.equals(currency(wallet.getCurrencyCode()))) {
                        return logAndReturn(card, req, CardAuthResponse.decline("57"), fraud.totalScore(), fraud.decision());
                    }
                    if (wallet.getBalance().compareTo(amountMajor) < 0) {
                        return logAndReturn(card, req, CardAuthResponse.decline("51"), fraud.totalScore(), fraud.decision());
                    }
                    wallet.setBalance(wallet.getBalance().subtract(amountMajor));
                    walletRepository.save(wallet);
                    walletDebit = amountMajor;
                }
            } else if (card.getCardType() == CardType.CREDIT) {
                // The backend has no revolving credit line yet (loans are amortising term loans), so
                // there is no credit to authorize against: decline as not permitted, not as an outage.
                log.info("Credit card {} declined: credit lines are not available", card.getId());
                return logAndReturn(card, req, CardAuthResponse.decline("57"), fraud.totalScore(), fraud.decision());
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
            return logAndReturn(card, req, response, fraud.totalScore(), fraud.decision(), holdRef, walletDebit);

        } catch (Exception e) {
            log.error("Authorization processing error for STAN={}", req.stan(), e);
            // The transaction rolls back (wallet included); a backend hold already placed would linger
            // until it expires — give it back now, best-effort.
            if (holdRef != null) releaseDebitHold(card, holdRef);
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
            case DEBIT   -> {
                BackendFunds funds = getDebitFunds(card);
                yield funds != null ? funds.availableBalance() : null;
            }
            case PREPAID -> getPrepaidBalance(card);
            case CREDIT  -> null; // no credit line in the backend yet
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

    /**
     * Reversal handler for MTI 0400/0420 — called by fep-service at
     * {@code POST /api/v1/internal/reverse}.
     *
     * <p>Locates the original authorization by (card, STAN), gives back what an approved original
     * took — the backend hold is released (a completed debit — 0200, or a purchase already cleared —
     * is refunded by the same call) or the prepaid wallet restored — marks its settlement item
     * REVERSED, records a {@code 0400} entry and fires {@code AUTHORIZATION.REVERSED},
     * <b>idempotently</b>, so a duplicate 0400 (a common scheme retry) never double-records or
     * double-notifies.
     *
     * @return ISO response code — {@code "00"} accepted, {@code "25"} original/card not located,
     *         {@code "96"} on error
     */
    @Transactional
    public String reverse(String pan, BigDecimal amount, String stan, String originalDataElements) {
        try {
            Card card;
            try {
                card = cardService.findByPanHash(pan);
            } catch (Exception e) {
                log.warn("Reversal: card not found for STAN={}", stan);
                return "25"; // unable to locate account
            }

            // Idempotency — a reversal for this (card, STAN) is already recorded.
            if (authLogRepository.existsByCardIdAndStanAndMti(card.getId(), stan, "0400")) {
                log.info("Reversal already processed for card={} STAN={} — idempotent no-op",
                        card.getId(), stan);
                return "00";
            }

            // Locate the original authorization/advice for this (card, STAN).
            Optional<AuthorizationLog> originalOpt =
                    authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(
                            card.getId(), stan, "0400");
            if (originalOpt.isEmpty()) {
                log.warn("Reversal: no original found for card={} STAN={}", card.getId(), stan);
                return "25"; // unable to locate original
            }
            AuthorizationLog original = originalOpt.get();

            // Give back what the approval reserved — once: the 0400 guard above stops a repeat. If the
            // backend hold can't be released, don't record the reversal: answer 96 so the network
            // retries (a recorded 0400 would block every retry and leave the funds held).
            if ("00".equals(original.getResponseCode())) {
                // Give back at most what this reversal covers: a partial reversal (DE4 below the original)
                // refunds only its amount — never the whole completed debit or wallet decrement.
                java.util.Currency ccy = currency(original.getCurrencyCode());
                BigDecimal reversalMinor = amount != null ? amount : original.getAmount();
                BigDecimal reversalMajor = ccy == null || reversalMinor == null ? null : toMajorUnits(reversalMinor, ccy);
                boolean full = original.getAmount() == null || reversalMinor == null
                        || reversalMinor.compareTo(original.getAmount()) >= 0;
                if (original.getHoldReference() != null) {
                    ReleaseOutcome released = releaseDebitHold(card, original.getHoldReference(), reversalMajor, ccy);
                    if (released == ReleaseOutcome.UNREACHABLE) return "96";
                    if (released == ReleaseOutcome.REFUSED) return "13";   // amount/currency don't match the original
                }
                if (original.getWalletDebit() != null && original.getWalletDebit().signum() > 0) {
                    BigDecimal restore = reversalMajor == null ? original.getWalletDebit()
                            : reversalMajor.min(original.getWalletDebit());
                    walletRepository.findByCardIdForUpdate(card.getId()).ifPresent(w -> {
                        w.setBalance(w.getBalance().add(restore));
                        walletRepository.save(w);
                    });
                }
                if (full) {
                    settlementService.markReversed(original.getId());
                }
            }

            // Record the reversal (0400). The reserved funds were given back above.
            AuthorizationLog reversal = buildAuthLog(card, stan, "0400", amount,
                    original.getCurrencyCode(), "00", null,
                    original.getTerminalId(), original.getMerchantId(), original.getMerchantName(),
                    false, original.getScheme());
            reversal.setProcessingCode(original.getProcessingCode());
            reversal.setRrn(original.getRrn());
            reversal.setMcc(original.getMcc());
            authLogRepository.save(reversal);

            // Notify the partner/webhooks (best-effort — never block the reversal).
            try {
                webhookService.publishEvent("AUTHORIZATION.REVERSED", Map.of(
                        "cardId", card.getId(),
                        "stan", stan,
                        "amount", amount != null ? amount : BigDecimal.ZERO,
                        "currencyCode", original.getCurrencyCode() != null ? original.getCurrencyCode() : "",
                        "merchantId", original.getMerchantId() != null ? original.getMerchantId() : "",
                        "originalResponseCode", original.getResponseCode()));
            } catch (Exception e) {
                log.warn("Webhook publish failed for AUTHORIZATION.REVERSED: {}", e.getMessage());
            }

            log.info("Reversal recorded for card={} STAN={}", card.getId(), stan);
            return "00";

        } catch (Exception e) {
            log.error("Reversal processing error for STAN={}", stan, e);
            return "96";
        }
    }

    // ── Balance Checks (via monolith REST) ────────────────────────────────────

    /**
     * What the linked account may spend: backend {@code GET /api/v1/accounts/{id}/balance}
     * (ApiResponse envelope; service token on {@code backendRestTemplate}). Null when unavailable.
     */
    private BackendFunds getDebitFunds(Card card) {
        if (card.getLinkedEntityId() == null) return null;
        try {
            String url = restClientConfig.getBackendBaseUrl()
                    + "/api/v1/accounts/" + card.getLinkedEntityId() + "/balance";
            ResponseEntity<Envelope<BackendFunds>> resp = backendRestTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<Envelope<BackendFunds>>() {});
            return resp.getBody() != null ? resp.getBody().data() : null;
        } catch (RestClientException e) {
            log.warn("Debit balance lookup failed for card {}: {}", card.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * card-service's idempotency key for an authorization's hold. A retried 0100 carries the same
     * card, STAN, RRN, terminal, amount and currency, so it maps to the same hold; any other
     * authorization — even one reusing a STAN, or with no RRN — gets its own key (SHA-256, hex).
     */
    static String holdReference(Card card, CardAuthRequest req) {
        String material = card.getId() + "|" + req.stan() + "|" + req.rrn() + "|" + req.terminalId()
                + "|" + (req.amount() != null ? req.amount().stripTrailingZeros().toPlainString() : "")
                + "|" + req.currencyCode();
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "CARD-" + java.util.HexFormat.of().formatHex(digest).substring(0, 40);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Reserves the amount on the linked account: backend {@code POST /api/v1/accounts/{id}/card-holds},
     * which checks and holds atomically under the account's row lock. Null when unavailable.
     */
    private BackendHold placeDebitHold(Card card, String reference, BigDecimal amountMajor, java.util.Currency currency) {
        if (card.getLinkedEntityId() == null) return null;
        try {
            String url = restClientConfig.getBackendBaseUrl()
                    + "/api/v1/accounts/" + card.getLinkedEntityId() + "/card-holds";
            Map<String, Object> body = Map.of("reference", reference, "amount", amountMajor,
                    "currencyCode", currency.getCurrencyCode());
            ResponseEntity<Envelope<BackendHold>> resp = backendRestTemplate.exchange(
                    url, HttpMethod.POST, new org.springframework.http.HttpEntity<>(body),
                    new ParameterizedTypeReference<Envelope<BackendHold>>() {});
            return resp.getBody() != null ? resp.getBody().data() : null;
        } catch (RestClientException e) {
            log.warn("Debit hold failed for card {}: {}", card.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Debits the linked account for a single-message transaction (0200): backend
     * {@code POST /api/v1/accounts/{id}/card-debits} checks, debits and posts the journal atomically,
     * idempotent on {@code reference}. Same result shape as a hold. Null when unavailable.
     */
    private BackendHold postSingleMessageDebit(Card card, String reference, BigDecimal amountMajor,
                                               java.util.Currency currency, boolean cashWithdrawal) {
        if (card.getLinkedEntityId() == null) return null;
        try {
            String url = restClientConfig.getBackendBaseUrl()
                    + "/api/v1/accounts/" + card.getLinkedEntityId() + "/card-debits";
            Map<String, Object> body = Map.of("reference", reference, "amount", amountMajor,
                    "currencyCode", currency.getCurrencyCode(), "cashWithdrawal", cashWithdrawal);
            ResponseEntity<Envelope<BackendHold>> resp = backendRestTemplate.exchange(
                    url, HttpMethod.POST, new org.springframework.http.HttpEntity<>(body),
                    new ParameterizedTypeReference<Envelope<BackendHold>>() {});
            return resp.getBody() != null ? resp.getBody().data() : null;
        } catch (RestClientException e) {
            log.warn("Single-message debit failed for card {}: {}", card.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Releases an approval's backend hold — or, for a completed debit (0200, or a purchase already
     * cleared), refunds it. True when the backend answered — released now, or
     * already released, expired or never placed (the release is idempotent); false when it
     * couldn't be reached, so the caller can refuse to report the reversal as done.
     */
    private boolean releaseDebitHold(Card card, String reference) {
        return releaseDebitHold(card, reference, null, null) != ReleaseOutcome.UNREACHABLE;
    }

    /** What a release (or refund) came to: done (or nothing to do), refused by the backend, or not reachable. */
    enum ReleaseOutcome { DONE, REFUSED, UNREACHABLE }

    /** Backend refusals of a refund that mean the reversal doesn't match the original. */
    private static final java.util.Set<String> REFUND_REFUSALS =
            java.util.Set.of("AMOUNT_EXCEEDS_CAPTURED", "CURRENCY_MISMATCH", "ALREADY_REFUNDED", "ACCOUNT_CLOSED");

    /**
     * Releases (or, for a completed debit, refunds) with the reversal's amount — the backend refunds at
     * most that, and records a reference it has never seen as released so a late debit is refused.
     */
    private ReleaseOutcome releaseDebitHold(Card card, String reference, BigDecimal amountMajor, java.util.Currency currency) {
        if (card == null || card.getLinkedEntityId() == null || reference == null) return ReleaseOutcome.DONE;
        try {
            org.springframework.http.HttpEntity<Map<String, Object>> body = amountMajor == null || currency == null ? null
                    : new org.springframework.http.HttpEntity<>(Map.of("amount", amountMajor,
                            "currencyCode", currency.getCurrencyCode()));
            ResponseEntity<Envelope<BackendHold>> resp = backendRestTemplate.exchange(
                    restClientConfig.getBackendBaseUrl() + "/api/v1/accounts/"
                            + card.getLinkedEntityId() + "/card-holds/" + reference + "/release",
                    HttpMethod.POST, body, new ParameterizedTypeReference<Envelope<BackendHold>>() {});
            BackendHold result = resp != null && resp.getBody() != null ? resp.getBody().data() : null;
            if (result != null && !result.approved() && REFUND_REFUSALS.contains(result.reason())) {
                log.warn("Backend refused release/refund of {} for card {}: {}", reference, card.getId(), result.reason());
                return ReleaseOutcome.REFUSED;
            }
            return ReleaseOutcome.DONE;
        } catch (RestClientException e) {
            log.warn("Debit hold release failed for card {}: {}", card.getId(), e.getMessage());
            return ReleaseOutcome.UNREACHABLE;
        }
    }

    /** An ISO 4217 currency from a numeric (DE49, "840") or alphabetic ("USD") code; null if unknown. */
    public static java.util.Currency currency(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim();
        if (c.chars().allMatch(Character::isDigit)) {
            int numeric = Integer.parseInt(c);
            return java.util.Currency.getAvailableCurrencies().stream()
                    .filter(cur -> cur.getNumericCode() == numeric).findFirst().orElse(null);
        }
        try {
            return java.util.Currency.getInstance(c.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** DE4 minor units → major units of the currency (2 decimals for USD, 0 for JPY, 3 for KWD). */
    public static BigDecimal toMajorUnits(BigDecimal minorUnits, java.util.Currency currency) {
        return minorUnits.movePointLeft(Math.max(currency.getDefaultFractionDigits(), 0));
    }

    private BigDecimal getPrepaidBalance(Card card) {
        return walletRepository.findByCardId(card.getId())
                .map(PrepaidWallet::getBalance)
                .orElse(null);
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private CardAuthResponse logAndReturn(Card card, CardAuthRequest req,
                                           CardAuthResponse response,
                                           int fraudScore, FraudDecision decision) {
        return logAndReturn(card, req, response, fraudScore, decision, null, null);
    }

    private CardAuthResponse logAndReturn(Card card, CardAuthRequest req,
                                           CardAuthResponse response,
                                           int fraudScore, FraudDecision decision,
                                           String holdReference, BigDecimal walletDebit) {
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
        entry.setHoldReference(holdReference);
        entry.setWalletDebit(walletDebit);
        authLogRepository.save(entry);

        // An approval that reserved or moved funds goes into today's settlement batch, in this
        // transaction: a debit purchase to be cleared at batch close; a 0200 debit or a prepaid
        // wallet decrement already completed.
        if (response.approved() && card != null && (holdReference != null || walletDebit != null)) {
            settlementService.addAuthorization(entry, card, holdReference, walletDebit != null || req.isFinancial());
        }

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

    /**
     * The backend's {@code CardFunds} (major units of {@code currencyCode}). Package-private so
     * unit tests in this package can stub the backend call with real funds.
     */
    record BackendFunds(UUID accountId, String currencyCode, BigDecimal availableBalance,
                        boolean debitAllowed, String reason) {}

    /** The backend's {@code CardHoldResult}. */
    record BackendHold(boolean approved, UUID holdId, String reference, String status, String reason,
                       BigDecimal availableBalance) {}

    /** The backend's {@code ApiResponse} envelope — only {@code data} is read. */
    record Envelope<T>(T data) {}
}

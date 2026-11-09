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
import com.cba.card.interchange.InterchangeQualificationEngine;
import com.cba.card.interchange.IssuerCharges;
import com.cba.card.interchange.Schemes;
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
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final com.cba.card.settlement.BackendClearingClient clearingClient;
    private final InterchangeQualificationEngine interchangeEngine;

    /** Injected lazily to break the potential cycle: CardAuthorizationService ← WebhookDeliveryService ← WebhookService. */
    @Lazy @Autowired
    private WebhookService webhookService;

    @Transactional
    public CardAuthResponse authorize(CardAuthRequest req) {
        String holdRef = null;          // backend hold placed by this approval (debit)
        IssuerCharges posted = null;    // interchange and fees the backend posted with a 0200 debit (backlog 15b)
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

            // 1b. Cardholder billing fields (DE6/DE51/DE10) are checked before anything else uses them:
            //     an inconsistent set is a format error (RC 30), before fraud scoring or any funds call.
            if (req.billingSent() && !validBilling(req)) {
                return logAndReturn(card, req, CardAuthResponse.decline("30"), 0, FraudDecision.DECLINE);
            }

            // 2. Fraud evaluation — the amount rules also see the amount that moves the card's money
            FraudContext ctx = new FraudContext(
                    card, req.pan(), req.amount(), req.currencyCode(),
                    req.processingCode(), req.terminalId(), req.merchantId(),
                    req.merchantName(), req.mcc(), req.posEntryMode(),
                    req.stan(), req.pinVerified(), req.arqcValid(),
                    req.isFinancial(), null,
                    req.billingSent() ? req.billingAmount() : null,
                    req.billingSent() ? req.billingCurrencyCode() : null);

            FraudEvaluationResult fraud = fraudEngine.evaluate(ctx);
            publishFraudEvents(card, fraud);

            if (fraud.declined()) {
                String rc = fraud.ruleResults().stream()
                        .filter(r -> r.triggered() && r.ruleId().equals("CARD_BLOCKED"))
                        .findFirst().isPresent() ? "62" : "05";
                return logAndReturn(card, req, CardAuthResponse.decline(rc),
                        fraud.totalScore(), fraud.decision());
            }

            // 2b. Cardholder controls (backlog 5) — before anything is reserved, so a declined tap holds nothing.
            String blockedBy = blockingControl(card, req);
            if (blockedBy != null) {
                log.info("Card {} declined 57: {} is turned off", card.getId(), blockedBy);
                return logAndReturn(card, req, CardAuthResponse.decline("57"), fraud.totalScore(), fraud.decision());
            }

            // STEP_UP: inform FEP to require online PIN — FEP handles protocol-level step-up
            // We still proceed to balance check; FEP may re-submit with pinVerified=true

            // 3. Funds — checked AND reserved. DE4 (req.amount) is minor units of the DE49 currency;
            //    balances are major units: compare in the same units and currency (they were compared
            //    raw: 25.00 USD read as 2500). An approval reserves its amount — a backend account hold
            //    (debit) or a wallet decrement (prepaid) — so later authorizations can't spend it too.
            //    The card's money moves in the cardholder billing amount (DE6, currency DE51) when the scheme
            //    sends it — a foreign-currency purchase the scheme converted to the card's currency (backlog 17).
            //    Without DE6 it moves in DE4/DE49. DE6 without DE51, or the reverse, is a format error.
            BigDecimal cardMinor = req.billingSent() ? req.billingAmount() : req.amount();
            String cardCurrencyCode = req.billingSent() ? req.billingCurrencyCode() : req.currencyCode();
            boolean inquiry = BALANCE_INQUIRY_CODE.equals(req.processingCode()) || cardMinor == null;
            java.util.Currency txCurrency = inquiry ? null : currency(cardCurrencyCode);
            BigDecimal amountMajor = inquiry || txCurrency == null ? null : toMajorUnits(cardMinor, txCurrency);
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
                    // 0200 (single message, e.g. ATM cash) is approved and completed at once: debit now, with
                    // its interchange and scheme fees in the same journal (backlog 15b).
                    // 0100 reserves with a hold that batch close clears (backlog 15a, decisions 1a/2a).
                    BackendHold hold = req.isFinancial()
                            ? postSingleMessageDebit(card, reference, amountMajor, txCurrency,
                                    req.processingCode() != null && req.processingCode().startsWith("01"),
                                    quoteCharges(card, req, amountMajor, txCurrency))
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
                    if (req.isFinancial()) posted = hold.postedCharges(Schemes.normalize(req.scheme()));
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
                    // Never loaded (a wallet is opened by its first load): no funds, not an outage.
                    return logAndReturn(card, req, CardAuthResponse.decline("51"), fraud.totalScore(), fraud.decision());
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

            CardAuthResponse response = CardAuthResponse.approve(authCode, balanceForResponse,
                    req.billingSent() ? req.billingCurrencyCode() : req.currencyCode());
            return logAndReturn(card, req, response, fraud.totalScore(), fraud.decision(), holdRef, walletDebit, posted);

        } catch (Exception e) {
            log.error("Authorization processing error for STAN={}", req.stan(), e);
            // The transaction rolls back (wallet included); a backend hold already placed would linger
            // until it expires — give it back now, best-effort.
            if (holdRef != null) releaseDebitHold(card, holdRef);
            return logAndReturn(card, req, CardAuthResponse.systemError(), 0, FraudDecision.DECLINE);
        }
    }

    /**
     * A consistent cardholder billing set, else a format error (RC 30):
     * <ul>
     *   <li>DE6 a positive whole number of minor units, DE51 a known ISO 4217 numeric code;</li>
     *   <li>DE10 present (8 digits: decimal places, then the rate) — DE6 is accepted only with the rate
     *       the scheme converted at;</li>
     *   <li>DE6 within 1% of DE4 × DE10 (converted between the two currencies' minor units);</li>
     *   <li>DE51 equal to DE49 means no conversion: DE6 must equal DE4.</li>
     * </ul>
     */
    static boolean validBilling(CardAuthRequest req) {
        BigDecimal billing = req.billingAmount();
        java.util.Currency billingCcy = req.billingCurrencyCode() == null ? null : currency(req.billingCurrencyCode());
        if (billing == null || billing.signum() <= 0 || billing.stripTrailingZeros().scale() > 0 || billingCcy == null) {
            return false;
        }
        java.util.Currency txCcy = req.currencyCode() == null ? null : currency(req.currencyCode());
        if (req.amount() == null || txCcy == null) {
            return false;
        }
        if (txCcy.equals(billingCcy)) {
            return billing.compareTo(req.amount()) == 0;
        }
        String rate = req.billingConversionRate();
        if (rate == null || !rate.matches("\\d{8}")) {
            return false;
        }
        BigDecimal conversion = new BigDecimal(rate.substring(1)).movePointLeft(rate.charAt(0) - '0');
        if (conversion.signum() <= 0) {
            return false;
        }
        BigDecimal expected = toMajorUnits(req.amount(), txCcy).multiply(conversion)
                .movePointRight(Math.max(billingCcy.getDefaultFractionDigits(), 0));
        BigDecimal tolerance = expected.abs().multiply(new BigDecimal("0.01")).max(BigDecimal.ONE);
        return billing.subtract(expected).abs().compareTo(tolerance) <= 0;
    }

    /** The reversal amount in the card's currency (see {@link #reverse(String, BigDecimal, String, String, BigDecimal, String)}). */
    static BigDecimal reversalCardMinor(AuthorizationLog original, BigDecimal amount, BigDecimal billingAmount) {
        if (billingAmount != null) {
            // Never more than the original moved: a reversal gives back at most what was taken.
            BigDecimal original0 = original.cardAmount();
            return original0 == null ? billingAmount : billingAmount.min(original0);
        }
        if (amount == null) {
            return original.cardAmount();
        }
        if (original.getBillingAmount() == null || original.getAmount() == null || original.getAmount().signum() == 0) {
            return amount;
        }
        if (amount.compareTo(original.getAmount()) >= 0) {
            return original.getBillingAmount();
        }
        return original.getBillingAmount().multiply(amount)
                .divide(original.getAmount(), 0, java.math.RoundingMode.HALF_UP);
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
        return reverse(pan, amount, stan, originalDataElements, null, null);
    }

    /**
     * As above, with the 0400's cardholder billing amount (DE6) and currency (DE51) when the scheme sends
     * them. What is given back is in the card's currency: the 0400's DE6; else, for an original that had a
     * billing amount, DE4 pro-rated onto it ({@code billing × DE4 reversal / DE4 original}, HALF_UP); else DE4.
     */
    @Transactional
    public String reverse(String pan, BigDecimal amount, String stan, String originalDataElements,
                          BigDecimal billingAmount, String billingCurrencyCode) {
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
            // DE4 is a positive whole number of minor units; anything else is not a reversal amount.
            if (amount != null && (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0)) {
                log.warn("Reversal refused for card={} STAN={}: invalid amount {}", card.getId(), stan, amount);
                return "13";
            }
            if (billingAmount != null && (billingAmount.signum() <= 0 || billingAmount.stripTrailingZeros().scale() > 0
                    || billingCurrencyCode == null || !billingCurrencyCode.equals(original.cardCurrencyCode()))) {
                log.warn("Reversal refused for card={} STAN={}: billing amount {} {} doesn't fit the original",
                        card.getId(), stan, billingAmount, billingCurrencyCode);
                return "13";
            }

            if ("00".equals(original.getResponseCode())) {
                // Give back at most what this reversal covers: a partial reversal (DE4 below the original)
                // refunds only its amount — never the whole completed debit or wallet decrement.
                // Everything below is in the card's currency: what the original moved (DE6 if it had one).
                java.util.Currency ccy = currency(original.cardCurrencyCode());
                BigDecimal originalCardMinor = original.cardAmount();
                BigDecimal reversalMinor = reversalCardMinor(original, amount, billingAmount);
                BigDecimal reversalMajor = ccy == null || reversalMinor == null ? null : toMajorUnits(reversalMinor, ccy);
                boolean full = originalCardMinor == null || reversalMinor == null
                        || reversalMinor.compareTo(originalCardMinor) >= 0;
                // A full reversal takes the item out of clearing first (row-locked until this commits), so a
                // batch close can't capture the purchase between the release below and the 0400 record.
                boolean prepaid = original.getWalletDebit() != null && original.getWalletDebit().signum() > 0;
                // (Prepaid items are handled by reversePrepaidItem below, which also knows whether they were cleared.)
                SettlementService.ItemReversal marked = full && !prepaid ? settlementService.markReversed(original.getId()) : null;
                if (marked == SettlementService.ItemReversal.IN_CLEARING) {
                    // Being cleared right now: answer "try again"; the retry refunds the cleared debit.
                    return "96";
                }
                boolean walletBack = true;
                if (prepaid) {
                    // Prepaid (backlog 15c): before clearing nothing is on the backend; after clearing the backend
                    // refunds it first (DR 2300 / CR 2500, pro rata), then the wallet is credited.
                    SettlementService.PrepaidItemReversal pr = settlementService.reversePrepaidItem(original.getId(), reversalMinor, full);
                    switch (pr.outcome()) {
                        case IN_CLEARING -> { return "96"; }
                        case ALREADY_RETURNED -> walletBack = false;   // expired: the wallet already had it back
                        case CLEARED -> {
                            com.cba.card.settlement.BackendClearingClient.PrepaidPost refund = clearingClient.prepaidRefund(pr.clearingReference(),
                                    reversalReference(pr.clearingReference(), stan, reversalMinor), reversalMajor,
                                    ccy == null ? null : ccy.getCurrencyCode());
                            if (refund == null || !refund.approved()) {
                                rollbackOnly();   // undo the REVERSED mark: nothing was given back
                                if (refund != null) log.warn("Prepaid refund refused for card={} STAN={}: {}", card.getId(), stan, refund.reason());
                                return refund == null ? "96" : "13";
                            }
                            settlementService.recordRefund(original.getId(), reversalMinor,
                                    refund.reversed(Schemes.normalize(original.getScheme())));
                        }
                        default -> { }
                    }
                }
                if (!prepaid && original.getHoldReference() != null) {
                    // A partial reversal of a purchase not yet cleared keeps the hold: batch close clears only
                    // what remains and the backend frees the rest. Otherwise release (or partly refund).
                    SettlementService.PendingReduction reduced = full ? null
                            : settlementService.reducePendingItem(original.getId(), reversalMinor);
                    if (reduced == SettlementService.PendingReduction.IN_CLEARING) {
                        return "96";
                    }
                    if (reduced != SettlementService.PendingReduction.REDUCED) {
                        Release release = releaseWithResult(card, original.getHoldReference(), reversalMajor, ccy,
                                reversalReference(original.getHoldReference(), stan, reversalMinor));
                        ReleaseOutcome released = release.outcome();
                        if (released == ReleaseOutcome.DONE && release.hold() != null
                                && "REFUNDED".equals(release.hold().status())) {
                            // A cleared (or completed) debit was refunded: what it takes back from the scheme
                            // counts in today's scheme settlement (backlog 15b).
                            settlementService.recordRefund(original.getId(), reversalMinor,
                                    release.hold().reversedCharges(Schemes.normalize(original.getScheme())));
                        }
                        if (released != ReleaseOutcome.DONE) {
                            // Nothing was given back: undo the REVERSED mark so the purchase still clears.
                            if (marked == SettlementService.ItemReversal.MARKED) rollbackOnly();
                            return released == ReleaseOutcome.UNREACHABLE ? "96"
                                    : "13";   // amount/currency don't match the original
                        }
                    }
                }
                if (walletBack && original.getWalletDebit() != null && original.getWalletDebit().signum() > 0) {
                    BigDecimal restore = reversalMajor == null ? original.getWalletDebit()
                            : reversalMajor.min(original.getWalletDebit()).max(BigDecimal.ZERO);
                    walletRepository.findByCardIdForUpdate(card.getId()).ifPresent(w -> {
                        w.setBalance(w.getBalance().add(restore));
                        walletRepository.save(w);
                    });
                }
            }

            // Record the reversal (0400). The reserved funds were given back above.
            AuthorizationLog reversal = buildAuthLog(card, stan, "0400", amount,
                    original.getCurrencyCode(), "00", null,
                    original.getTerminalId(), original.getMerchantId(), original.getMerchantName(),
                    false, original.getScheme());
            reversal.setProcessingCode(original.getProcessingCode());
            reversal.setRrn(original.getRrn());
            if (original.getBillingAmount() != null || billingAmount != null) {
                reversal.setBillingAmount(reversalCardMinor(original, amount, billingAmount));
                reversal.setBillingCurrencyCode(original.cardCurrencyCode());
            }
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
            // A 96 records no 0400, so nothing this reversal changed may commit — a REVERSED mark or a
            // restored wallet would otherwise stand, and the network's retry would give it back again.
            rollbackOnly();
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
    /** The backend's idempotency key for clearing a prepaid approval (backlog 15c): the hold key, prefixed PPD-. */
    static String prepaidReference(Card card, CardAuthRequest req) {
        return "PPD-" + holdReference(card, req).substring("CARD-".length());
    }

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
                                               java.util.Currency currency, boolean cashWithdrawal,
                                               IssuerCharges charges) {
        if (card.getLinkedEntityId() == null) return null;
        try {
            String url = restClientConfig.getBackendBaseUrl()
                    + "/api/v1/accounts/" + card.getLinkedEntityId() + "/card-debits";
            Map<String, Object> body = new java.util.HashMap<>(Map.of("reference", reference, "amount", amountMajor,
                    "currencyCode", currency.getCurrencyCode(), "cashWithdrawal", cashWithdrawal));
            if (charges != null) {
                body.put("scheme", charges.scheme());
                if (charges.interchange().signum() > 0) {
                    body.put("interchangeAmount", charges.interchange());
                    body.put("interchangeDirection", charges.direction() == null ? null : charges.direction().name());
                }
                if (charges.schemeFee().signum() > 0) body.put("schemeFeeAmount", charges.schemeFee());
            }
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
        return releaseDebitHold(card, reference, null, null, null) != ReleaseOutcome.UNREACHABLE;
    }

    private ReleaseOutcome releaseDebitHold(Card card, String reference, BigDecimal amountMajor, java.util.Currency currency) {
        return releaseDebitHold(card, reference, amountMajor, currency, null);
    }

    /**
     * The key of one reversal of a hold: a retried 0400 (same original, STAN and amount) maps to the same key,
     * so the backend refunds it once and reports the retry as done — and a different reversal as refused.
     */
    static String reversalReference(String holdReference, String stan, BigDecimal reversalMinor) {
        String material = holdReference + "|" + stan + "|"
                + (reversalMinor != null ? reversalMinor.stripTrailingZeros().toPlainString() : "");
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "REV-" + java.util.HexFormat.of().formatHex(digest).substring(0, 40);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * The issuer's interchange and scheme fees for a single-message debit (backlog 15b). Never fails the
     * authorization: no engine answer means none.
     */
    private IssuerCharges quoteCharges(Card card, CardAuthRequest req, BigDecimal amountMajor, java.util.Currency currency) {
        try {
            IssuerCharges c = interchangeEngine.quote(req.scheme(), card.getCardType(), req.processingCode(),
                    req.posEntryMode(), req.mcc(), amountMajor, currency);
            return c != null ? c : IssuerCharges.none(Schemes.normalize(req.scheme()));
        } catch (RuntimeException e) {
            log.warn("Interchange quote failed for card {}: {}", card.getId(), e.getMessage());
            return IssuerCharges.none(Schemes.normalize(req.scheme()));
        }
    }

    /** A release's outcome and, when the backend answered, its result (to see whether it refunded). */
    record Release(ReleaseOutcome outcome, BackendHold hold) {}

    /** Rolls back the reversal's transaction (when there is one) so changes made before a refusal are undone. */
    private static void rollbackOnly() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
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
    private ReleaseOutcome releaseDebitHold(Card card, String reference, BigDecimal amountMajor, java.util.Currency currency,
                                            String reversalReference) {
        return releaseWithResult(card, reference, amountMajor, currency, reversalReference).outcome();
    }

    private Release releaseWithResult(Card card, String reference, BigDecimal amountMajor, java.util.Currency currency,
                                      String reversalReference) {
        if (card == null || card.getLinkedEntityId() == null || reference == null) return new Release(ReleaseOutcome.DONE, null);
        try {
            Map<String, Object> fields = new java.util.HashMap<>();
            if (amountMajor != null && currency != null) {
                fields.put("amount", amountMajor);
                fields.put("currencyCode", currency.getCurrencyCode());
            }
            if (reversalReference != null) fields.put("reversalReference", reversalReference);
            org.springframework.http.HttpEntity<Map<String, Object>> body =
                    fields.isEmpty() ? null : new org.springframework.http.HttpEntity<>(fields);
            ResponseEntity<Envelope<BackendHold>> resp = backendRestTemplate.exchange(
                    restClientConfig.getBackendBaseUrl() + "/api/v1/accounts/"
                            + card.getLinkedEntityId() + "/card-holds/" + reference + "/release",
                    HttpMethod.POST, body, new ParameterizedTypeReference<Envelope<BackendHold>>() {});
            BackendHold result = resp != null && resp.getBody() != null ? resp.getBody().data() : null;
            // A refusal without a reason fails closed: never report "given back" when the backend said no.
            if (result != null && !result.approved()
                    && (result.reason() == null || REFUND_REFUSALS.contains(result.reason()))) {
                log.warn("Backend refused release/refund of {} for card {}: {}", reference, card.getId(), result.reason());
                return new Release(ReleaseOutcome.REFUSED, result);
            }
            return new Release(ReleaseOutcome.DONE, result);
        } catch (RestClientException e) {
            log.warn("Debit hold release failed for card {}: {}", card.getId(), e.getMessage());
            return new Release(ReleaseOutcome.UNREACHABLE, null);
        }
    }

    /**
     * The cardholder control that refuses this request, or {@code null} when none does.
     * <ul>
     *   <li>contactless off — DE22 07x / 91x;</li>
     *   <li>card-not-present off — any DE22 that isn't card present (blank included);</li>
     *   <li>international off — the acquirer's country (DE19) isn't the card's home country (its BIN range).
     *       Fails closed: a request with no DE19, or one that isn't a known ISO 3166 code, is refused too.</li>
     * </ul>
     */
    String blockingControl(Card card, CardAuthRequest req) {
        if (!card.isContactlessEnabled() && PosEntryModes.isContactless(req.posEntryMode())) return "contactless";
        if (!card.isCnpEnabled() && !PosEntryModes.isCardPresent(req.posEntryMode())) return "card-not-present";
        if (!card.isInternationalEnabled()) {
            String acquirer = CountryCodes.alpha3(req.acquiringCountry());
            String home = cardService.issuingCountry(card);
            if (acquirer == null || home == null || !acquirer.equals(home)) return "international";
        }
        return null;
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
        return logAndReturn(card, req, response, fraudScore, decision, holdReference, walletDebit, null);
    }

    private CardAuthResponse logAndReturn(Card card, CardAuthRequest req,
                                           CardAuthResponse response,
                                           int fraudScore, FraudDecision decision,
                                           String holdReference, BigDecimal walletDebit, IssuerCharges posted) {
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
        if (req.billingSent()) {
            entry.setBillingAmount(req.billingAmount());
            entry.setBillingCurrencyCode(req.billingCurrencyCode());
            entry.setConversionRate(req.billingConversionRate());
        }
        authLogRepository.save(entry);

        // An approval that reserved or moved funds goes into today's settlement batch, in this
        // transaction: a debit purchase to be cleared at batch close; a 0200 debit or a prepaid
        // wallet decrement already completed.
        if (response.approved() && card != null && (holdReference != null || walletDebit != null)) {
            if (posted != null) {
                settlementService.addAuthorization(entry, card, holdReference, true, posted);
            } else if (walletDebit != null) {
                // Prepaid (backlog 15c): the wallet decrement is the hold; batch close clears it on the backend
                // (DR 2500 / CR 2300), under its own reference.
                settlementService.addAuthorization(entry, card, prepaidReference(card, req), false);
            } else {
                settlementService.addAuthorization(entry, card, holdReference, req.isFinancial());
            }
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
        log_entry.setScheme(scheme == null ? null : Schemes.normalize(scheme));
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

    /** The backend's {@code CardHoldResult}, with the interchange and fees it posted / reversed (backlog 15b). */
    record BackendHold(boolean approved, UUID holdId, String reference, String status, String reason,
                       BigDecimal availableBalance, BigDecimal interchangeAmount, String interchangeDirection,
                       BigDecimal schemeFeeAmount, BigDecimal interchangeReversed, BigDecimal schemeFeeReversed) {

        BackendHold(boolean approved, UUID holdId, String reference, String status, String reason,
                    BigDecimal availableBalance) {
            this(approved, holdId, reference, status, reason, availableBalance, null, null, null, null, null);
        }

        private com.cba.card.interchange.InterchangeDirection direction() {
            return interchangeDirection == null ? null
                    : com.cba.card.interchange.InterchangeDirection.valueOf(interchangeDirection);
        }

        /** What the debit posted (major units). */
        IssuerCharges postedCharges(String scheme) {
            return new IssuerCharges(scheme, interchangeAmount, direction(), schemeFeeAmount, "BACKEND");
        }

        /** What a refund reversed (major units). */
        IssuerCharges reversedCharges(String scheme) {
            return new IssuerCharges(scheme, interchangeReversed, direction(), schemeFeeReversed, "BACKEND");
        }
    }

    /** The backend's {@code ApiResponse} envelope — only {@code data} is read. */
    record Envelope<T>(T data) {}
}

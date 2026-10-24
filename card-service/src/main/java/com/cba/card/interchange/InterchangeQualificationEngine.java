package com.cba.card.interchange;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.auth.CardAuthorizationService;
import com.cba.card.card.Card;
import com.cba.card.card.CardRepository;
import com.cba.card.card.CardType;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Interchange and scheme fees for a card transaction, from the <b>issuer's</b> side (backlog 15b): on a
 * purchase the acquirer pays the issuer interchange (EARNED); on cash at another bank's ATM the issuer
 * pays it (PAID); the issuer pays the scheme its fees either way.
 *
 * <h3>Qualification</h3>
 * <ol>
 *   <li>Card type from the card; PREPAID gets nothing until prepaid has a GL (backlog 15c).</li>
 *   <li>Channel from the POS entry mode — DE22 codes (05x chip, 07x contactless, 02x/90x magstripe = card
 *       present; 01x manual, 81x e-commerce = card not present) or the simulator's names.</li>
 *   <li>Transaction type from DE3: 01 cash, 20 refund (no interchange), else purchase.</li>
 *   <li>The most specific active rate (exact MCC first, then the catch-all); none → zero, logged.</li>
 *   <li>Scheme fees: ASSESSMENT and NETWORK only — cross-border and currency-conversion fees need the
 *       merchant's country, which isn't captured yet (backlog 15d).</li>
 * </ol>
 * Fixed fees are major units of the row's currency and apply only to transactions in that currency
 * (card-service has no FX). Figures are rounded once, to the currency's digits. {@link #quote} never
 * throws: authorization and clearing must not fail on fee configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterchangeQualificationEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Set<SchemeFeeType> APPLIED_FEES = EnumSet.of(SchemeFeeType.ASSESSMENT, SchemeFeeType.NETWORK);

    private final InterchangeRateRepository rateRepo;
    private final SchemeFeeRepository       feeRepo;
    private final InterchangeLogRepository  logRepo;
    private final CardRepository            cardRepo;
    private final AuthorizationLogRepository authRepo;

    /**
     * The issuer's interchange and scheme fees on one transaction (no persistence).
     *
     * @param grossMajor transaction amount, major units of {@code currency}
     */
    public IssuerCharges quote(String scheme, CardType cardType, String processingCode, String entryMode,
                               String mcc, BigDecimal grossMajor, Currency currency) {
        String s = Schemes.normalize(scheme);
        try {
            if (cardType == CardType.PREPAID || grossMajor == null || grossMajor.signum() <= 0 || currency == null) {
                return IssuerCharges.none(s);
            }
            TransactionType txn = mapTransactionType(processingCode);
            if (txn == TransactionType.REFUND) return IssuerCharges.none(s);
            int digits = Math.max(currency.getDefaultFractionDigits(), 0);
            String ccy = currency.getCurrencyCode();
            ChannelType channel = mapChannel(entryMode);

            InterchangeRate rate = qualifyRate(s, cardType == null ? CardType.DEBIT : cardType, txn, channel, mcc);
            BigDecimal interchange = BigDecimal.ZERO;
            String applied;
            if (rate != null) {
                BigDecimal raw = percent(grossMajor, rate.getRatePercent());
                applied = buildRateDescription(rate);
                if (fixedFeeApplies(rate.getCurrencyCode(), ccy)) {
                    raw = raw.add(rate.getFixedFee());
                } else if (rate.getFixedFee().signum() > 0) {
                    applied += " (fixed fee skipped: rate in " + rate.getCurrencyCode() + ", transaction in " + ccy + ")";
                    log.warn("Interchange fixed fee skipped for {}: rate currency {} ≠ transaction currency {}",
                            s, rate.getCurrencyCode(), ccy);
                }
                interchange = raw.setScale(digits, RoundingMode.HALF_UP);
            } else {
                applied = "NO_RATE_CONFIGURED";
                log.warn("No interchange rate for scheme={} cardType={} txn={} channel={} mcc={} — interchange 0",
                        s, cardType, txn, channel, mcc);
            }

            BigDecimal feeRaw = BigDecimal.ZERO;
            for (SchemeFee f : feeRepo.findActiveByScheme(s, LocalDate.now())) {
                if (!APPLIED_FEES.contains(f.getFeeType())) continue;
                feeRaw = feeRaw.add(percent(grossMajor, f.getRatePercent()));
                if (fixedFeeApplies(f.getCurrencyCode(), ccy)) feeRaw = feeRaw.add(f.getFixedFee());
            }
            InterchangeDirection direction = txn == TransactionType.CASH ? InterchangeDirection.PAID : InterchangeDirection.EARNED;
            return new IssuerCharges(s, interchange, direction, feeRaw.setScale(digits, RoundingMode.HALF_UP), applied);
        } catch (RuntimeException e) {
            log.error("Interchange quote failed for scheme={} — posting no interchange or fees", s, e);
            return IssuerCharges.none(s);
        }
    }

    /** Writes the calculation to {@code interchange_log} (major units, issuer's view). */
    @Transactional
    public void record(UUID authorizationLogId, UUID settlementItemId, IssuerCharges charges,
                       BigDecimal grossMajor, String currencyCode) {
        InterchangeLog row = new InterchangeLog();
        row.setAuthorizationLogId(authorizationLogId);
        row.setSettlementItemId(settlementItemId);
        row.setScheme(charges.scheme());
        row.setGrossAmount(grossMajor);
        row.setCurrencyCode(currencyCode);
        row.setInterchangeAmount(charges.interchange());
        row.setInterchangeDirection(charges.direction());
        row.setSchemeFeeAmount(charges.schemeFee());
        row.setNetSettlementAmount(charges.owedToScheme(grossMajor == null ? BigDecimal.ZERO : grossMajor));
        row.setRateApplied(truncate(charges.rateApplied()));
        logRepo.save(row);
    }

    /**
     * Diagnostic calculation for one authorization (ADMIN {@code /interchange/calculate}); recorded in
     * {@code interchange_log}.
     *
     * @throws CbaException (NOT_FOUND) if the auth log doesn't exist
     */
    @Transactional
    public InterchangeResult calculateForAuth(UUID authorizationLogId) {
        AuthorizationLog auth = authRepo.findById(authorizationLogId)
                .orElseThrow(() -> CbaException.notFound("AUTH_NOT_FOUND",
                        "Authorization log not found: " + authorizationLogId));
        return calculate(auth);
    }

    /** As {@link #calculateForAuth}, for an authorization in hand. */
    @Transactional
    public InterchangeResult calculate(AuthorizationLog auth) {
        Currency currency = CardAuthorizationService.currency(auth.getCurrencyCode());
        BigDecimal grossMajor = currency == null || auth.getAmount() == null
                ? BigDecimal.ZERO : CardAuthorizationService.toMajorUnits(auth.getAmount(), currency);
        IssuerCharges charges = quote(auth.getScheme(), resolveCardType(auth.getCardId()), auth.getProcessingCode(),
                auth.getEntryMode(), auth.getMcc(), grossMajor, currency);
        record(auth.getId(), null, charges, grossMajor, currency == null ? null : currency.getCurrencyCode());
        return new InterchangeResult(charges.interchange(), charges.schemeFee(), charges.owedToScheme(grossMajor),
                charges.rateApplied(), charges.direction());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static BigDecimal percent(BigDecimal amount, BigDecimal ratePercent) {
        return amount.multiply(ratePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }

    private static boolean fixedFeeApplies(String feeCurrency, String transactionCurrency) {
        return feeCurrency == null || feeCurrency.equalsIgnoreCase(transactionCurrency);
    }

    private CardType resolveCardType(UUID cardId) {
        if (cardId == null) return CardType.DEBIT; // conservative default
        return cardRepo.findById(cardId)
                .map(Card::getCardType)
                .orElse(CardType.DEBIT);
    }

    /**
     * POS entry mode → channel. DE22's first two digits: 02/05/07/80/90/91 are card present (magstripe,
     * chip, contactless, fallback); 01 (manual), 81 (e-commerce) and anything else are not. The terminal
     * simulator's names (CHIP, CONTACTLESS, SWIPE) are accepted too.
     */
    static ChannelType mapChannel(String entryMode) {
        if (entryMode == null || entryMode.isBlank()) return ChannelType.CNP;
        String m = entryMode.trim().toUpperCase(Locale.ROOT);
        return switch (m) {
            case "CHIP", "CONTACTLESS", "SWIPE" -> ChannelType.CARD_PRESENT;
            default -> m.length() >= 2 && Set.of("02", "05", "07", "80", "90", "91").contains(m.substring(0, 2))
                    ? ChannelType.CARD_PRESENT : ChannelType.CNP;
        };
    }

    /**
     * ISO 8583 processing code (DE3) → transaction type: 01xxxx cash, 20xxxx refund, anything else a
     * purchase (000000; 310000 balance enquiries carry no amount).
     */
    private static TransactionType mapTransactionType(String processingCode) {
        if (processingCode == null) return TransactionType.PURCHASE;
        return switch (processingCode.substring(0, Math.min(2, processingCode.length()))) {
            case "01" -> TransactionType.CASH;
            case "20" -> TransactionType.REFUND;
            default   -> TransactionType.PURCHASE;
        };
    }

    /** The most specific active rate: exact MCC first, then the catch-all (null MCC). */
    private InterchangeRate qualifyRate(String scheme, CardType cardType,
                                        TransactionType txnType, ChannelType channel, String mcc) {
        List<InterchangeRate> matches = rateRepo.findBestMatch(scheme, cardType, txnType, channel, mcc, LocalDate.now());
        if (matches.isEmpty()) return null;
        InterchangeRate best = matches.get(0);
        if (mcc != null && !mcc.isBlank() && best.getMccCategory() == null) {
            log.debug("Interchange downgrade: no specific rate for MCC={} — using catch-all", mcc);
        }
        return best;
    }

    private static String buildRateDescription(InterchangeRate r) {
        return String.format(Locale.ROOT, "%s/%s/%s/%s%s %.4f%% + %.4f %s",
                r.getScheme(), r.getCardType(), r.getTransactionType(), r.getChannel(),
                r.getMccCategory() != null ? "/" + r.getMccCategory() : "",
                r.getRatePercent(), r.getFixedFee(), r.getCurrencyCode());
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 200 ? s : s.substring(0, 200);
    }
}

package com.cba.card.interchange;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
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
import java.util.List;
import java.util.UUID;

/**
 * Calculates interchange and scheme fees for a card authorization.
 *
 * <h3>Qualification flow</h3>
 * <ol>
 *   <li>Resolve card type (DEBIT / PREPAID / CREDIT) from the card record.</li>
 *   <li>Map POS entry mode → {@link ChannelType}.</li>
 *   <li>Map ISO 8583 processing code → {@link TransactionType}.</li>
 *   <li>Query {@code interchange_rates} for the most specific matching row
 *       (MCC-specific first, catch-all null-MCC second).</li>
 *   <li>Apply downgrade: if no exact-MCC rate is found, fall back to the
 *       catch-all row. If no catch-all either, interchange = 0.</li>
 *   <li>Sum all active {@code scheme_fees} for the scheme.</li>
 *   <li>Return {@link InterchangeResult} and persist to {@code interchange_log}.</li>
 * </ol>
 *
 * <p>This class is a {@code @Component} (not {@code @Service}) because it
 * contains no business transaction boundary of its own — the calling
 * {@link InterchangeService} or {@link com.cba.card.settlement.SettlementService}
 * owns the transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterchangeQualificationEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InterchangeRateRepository rateRepo;
    private final SchemeFeeRepository       feeRepo;
    private final InterchangeLogRepository  logRepo;
    private final CardRepository            cardRepo;
    private final AuthorizationLogRepository authRepo;

    /**
     * Calculate interchange for a known authorization log entry by ID.
     * Looks up the auth log, then delegates to {@link #calculate(AuthorizationLog)}.
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

    /**
     * Calculate interchange for an in-memory authorization log entry.
     * Persists the result to {@code interchange_log}.
     */
    @Transactional
    public InterchangeResult calculate(AuthorizationLog auth) {
        BigDecimal gross = auth.getAmount() != null ? auth.getAmount() : BigDecimal.ZERO;
        if (gross.compareTo(BigDecimal.ZERO) == 0) {
            return persist(auth, InterchangeResult.noRate(BigDecimal.ZERO));
        }

        // ── 1. Resolve card type ──────────────────────────────────────────────
        CardType cardType = resolveCardType(auth.getCardId());

        // ── 2. Map channel from POS entry mode ───────────────────────────────
        ChannelType channel = mapChannel(auth.getEntryMode());

        // ── 3. Map transaction type from processing code ──────────────────────
        TransactionType txnType = mapTransactionType(auth.getProcessingCode());

        // ── 4. Determine scheme ───────────────────────────────────────────────
        String scheme = auth.getScheme() != null ? auth.getScheme().toUpperCase() : "UNKNOWN";

        // ── 5. Qualify interchange rate (MCC-specific → catch-all) ───────────
        InterchangeRate rate = qualifyRate(scheme, cardType, txnType, channel, auth.getMcc());

        // ── 6. Calculate interchange amount ──────────────────────────────────
        BigDecimal interchange;
        String rateApplied;
        if (rate != null) {
            interchange = gross.multiply(rate.getRatePercent())
                               .divide(HUNDRED, 4, RoundingMode.HALF_UP)
                               .add(rate.getFixedFee())
                               .setScale(4, RoundingMode.HALF_UP);
            rateApplied = buildRateDescription(rate);
        } else {
            interchange = BigDecimal.ZERO;
            rateApplied = "NO_RATE_CONFIGURED";
            log.debug("No interchange rate found for scheme={} cardType={} txn={} channel={} mcc={}",
                    scheme, cardType, txnType, channel, auth.getMcc());
        }

        // ── 7. Sum scheme fees ────────────────────────────────────────────────
        List<SchemeFee> fees = feeRepo.findActiveByScheme(scheme, LocalDate.now());
        BigDecimal schemeFeeTotal = fees.stream()
                .map(f -> gross.multiply(f.getRatePercent())
                               .divide(HUNDRED, 4, RoundingMode.HALF_UP)
                               .add(f.getFixedFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);

        // ── 8. Net settlement ─────────────────────────────────────────────────
        BigDecimal net = gross.subtract(interchange).subtract(schemeFeeTotal)
                              .setScale(4, RoundingMode.HALF_UP);

        InterchangeResult result = new InterchangeResult(interchange, schemeFeeTotal, net, rateApplied);
        return persist(auth, result);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CardType resolveCardType(UUID cardId) {
        if (cardId == null) return CardType.DEBIT; // conservative default
        return cardRepo.findById(cardId)
                .map(Card::getCardType)
                .orElse(CardType.DEBIT);
    }

    /**
     * Map POS entry mode string → channel.
     * CHIP and CONTACTLESS are EMV-qualified card-present.
     * SWIPE (mag stripe) is also card-present but may attract higher rates
     * via separate rate rows seeded in the DB.
     */
    private ChannelType mapChannel(String entryMode) {
        if (entryMode == null) return ChannelType.CNP;
        return switch (entryMode.toUpperCase()) {
            case "CHIP", "CONTACTLESS", "SWIPE" -> ChannelType.CARD_PRESENT;
            default -> ChannelType.CNP;
        };
    }

    /**
     * Map ISO 8583 processing code (DE3) → transaction type.
     *
     * <pre>
     * 000000 — purchase
     * 010000 — cash withdrawal
     * 200000 — refund / credit
     * 310000 — balance enquiry (not billable — treated as zero-amount)
     * </pre>
     */
    private TransactionType mapTransactionType(String processingCode) {
        if (processingCode == null) return TransactionType.PURCHASE;
        return switch (processingCode.substring(0, Math.min(2, processingCode.length()))) {
            case "01" -> TransactionType.CASH;
            case "20" -> TransactionType.REFUND;
            default   -> TransactionType.PURCHASE;
        };
    }

    /**
     * Find the most specific active rate for this transaction combination.
     * Attempts exact MCC match first; falls back to catch-all (null MCC).
     */
    private InterchangeRate qualifyRate(String scheme, CardType cardType,
                                         TransactionType txnType, ChannelType channel,
                                         String mcc) {
        List<InterchangeRate> matches = rateRepo.findBestMatch(
                scheme, cardType, txnType, channel, mcc, LocalDate.now());

        if (!matches.isEmpty()) {
            InterchangeRate best = matches.get(0);
            // If the best match is a catch-all but we had a specific MCC, log downgrade
            if (mcc != null && !mcc.isBlank() && best.getMccCategory() == null) {
                log.debug("Interchange downgrade: no specific rate for MCC={} — using catch-all", mcc);
            }
            return best;
        }
        return null;
    }

    private String buildRateDescription(InterchangeRate r) {
        return String.format("%s/%s/%s/%s%s %.4f%% + %.4f",
                r.getScheme(), r.getCardType(), r.getTransactionType(), r.getChannel(),
                r.getMccCategory() != null ? "/" + r.getMccCategory() : "",
                r.getRatePercent(), r.getFixedFee());
    }

    private InterchangeResult persist(AuthorizationLog auth, InterchangeResult result) {
        InterchangeLog log = new InterchangeLog();
        log.setAuthorizationLogId(auth.getId());
        log.setScheme(auth.getScheme() != null ? auth.getScheme() : "UNKNOWN");
        log.setInterchangeAmount(result.interchangeAmount());
        log.setSchemeFeeAmount(result.schemeFeeAmount());
        log.setNetSettlementAmount(result.netSettlementAmount());
        log.setRateApplied(result.rateApplied());
        logRepo.save(log);
        return result;
    }
}

package com.cba.accounting;

import com.cba.accounting.FinancialActivityAccount.FinancialActivity;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.currency.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Retranslates the bank's open foreign-currency positions at the closing rate and takes
 * the exchange difference to profit or loss (IAS 21 §23, §28).
 *
 * <p>Every foreign-currency movement is balanced in its own currency through the FX
 * position account (1200), so that account holds the bank's whole net exposure in each
 * currency. Its functional-currency carrying amount is the FX position equivalent (1201)
 * tagged with that currency. For each currency the job moves the carrying amount to
 * {@code -position × closing rate}: a debit position balances what the bank owes in the
 * currency, so it is a short position and a rising rate is a loss.
 *
 * <p>The closing rate is the active rate at run time: exchange rates have no history, so
 * the job must run at the close of the business day it revalues. Running it again on the
 * same day posts nothing, because the carrying amounts already equal the revalued ones.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FxRevaluationService {

    /** Net FX position per currency (1200), up to and including the business date. */
    private static final String POSITIONS_SQL = """
            SELECT currency_code AS k,
                   SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) AS net
            FROM journal_entries
            WHERE gl_account_id = ? AND transaction_date <= ?
            GROUP BY currency_code
            """;

    /** Functional-currency carrying amount of each position (1201), by the currency it values. */
    private static final String CARRYING_SQL = """
            SELECT position_currency AS k,
                   SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) AS net
            FROM journal_entries
            WHERE gl_account_id = ? AND currency_code = ? AND transaction_date <= ?
            GROUP BY position_currency
            """;

    private final GlAccountingService gl;
    private final FunctionalCurrency functionalCurrency;
    private final ExchangeRateService exchangeRateService;
    private final JdbcTemplate jdbcTemplate;

    /** One currency's revaluation; {@code transactionId} is null when nothing was posted. */
    public record CurrencyRevaluation(String currency, BigDecimal position, BigDecimal closingRate,
                                      BigDecimal carryingAmount, BigDecimal revaluedAmount,
                                      BigDecimal adjustment, String transactionId) {}

    /**
     * Revalues every open position as at {@code businessDate}. All currencies post in one
     * transaction: a currency without a closing rate fails the run and nothing is posted.
     */
    @Transactional
    public List<CurrencyRevaluation> revalue(LocalDate businessDate) {
        String functional = functionalCurrency.get();
        GlAccount position = gl.activityAccount(FinancialActivity.ASSET_FX_POSITION);
        GlAccount equivalent = gl.activityAccount(FinancialActivity.ASSET_FX_POSITION_EQUIVALENT);
        GlAccount gainLoss = gl.activityAccount(FinancialActivity.INCOME_FX_GAIN_LOSS);

        Map<String, BigDecimal> positions = net(POSITIONS_SQL, position.getId(), businessDate);
        Map<String, BigDecimal> carrying = net(CARRYING_SQL, equivalent.getId(), functional, businessDate);

        BigDecimal untagged = carrying.getOrDefault(null, BigDecimal.ZERO);
        if (untagged.signum() != 0) {
            throw new IllegalStateException("FX position equivalent (" + equivalent.getGlCode()
                    + ") holds " + untagged + " " + functional + " not attributed to any currency;"
                    + " it cannot be revalued. Correct the entries before revaluing.");
        }

        TreeSet<String> currencies = new TreeSet<>();
        positions.keySet().stream().filter(Objects::nonNull).forEach(currencies::add);
        carrying.keySet().stream().filter(Objects::nonNull).forEach(currencies::add);
        currencies.removeIf(c -> c.equalsIgnoreCase(functional));

        List<CurrencyRevaluation> results = new ArrayList<>();
        for (String currency : currencies) {
            BigDecimal pos = positions.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal carried = carrying.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal rate = exchangeRateService.getRate(currency, functional).getRate();
            BigDecimal revalued = pos.negate().multiply(rate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal adjustment = revalued.subtract(carried);

            String transactionId = null;
            if (adjustment.signum() != 0) {
                List<JournalLine> lines = adjustment.signum() > 0
                        ? List.of(JournalLine.debit(equivalent, adjustment, functional).withPosition(currency),
                                  JournalLine.credit(gainLoss, adjustment, functional))
                        : List.of(JournalLine.credit(equivalent, adjustment.negate(), functional).withPosition(currency),
                                  JournalLine.debit(gainLoss, adjustment.negate(), functional));
                transactionId = gl.postJournal(lines, businessDate,
                        "FX revaluation of " + currency + " position " + pos + " at closing rate " + rate
                                + " (IAS 21 §23)",
                        JournalEntry.EntityType.FX_REVALUATION, null,
                        "FXREV-" + businessDate + "-" + currency);
            }
            results.add(new CurrencyRevaluation(currency, pos, rate, carried, revalued, adjustment, transactionId));
            log.info("FX revaluation {} {}: position {} rate {} carrying {} -> {} (adjustment {})",
                    businessDate, currency, pos, rate, carried, revalued, adjustment);
        }
        return results;
    }

    /** Debits minus credits by key (currency); a null key collects untagged lines. */
    private Map<String, BigDecimal> net(String sql, Object... args) {
        Map<String, BigDecimal> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getString("k"), rs.getBigDecimal("net"));
        }, args);
        return result;
    }
}

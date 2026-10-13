package com.cba.loan;

import com.cba.accounting.FinancialActivityAccount.FinancialActivity;
import com.cba.accounting.FunctionalCurrency;
import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountingService;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.accounting.JournalEntry;
import com.cba.accounting.JournalEntryRepository;
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
import java.util.EnumSet;
import java.util.List;

/**
 * The CBN regulatory risk reserve. Banks book IFRS 9 impairment, then compare it with the
 * provisions the CBN Prudential Guidelines require: when the prudential provisions are
 * greater, the difference moves from the general reserve (retained earnings) to a
 * non-distributable regulatory risk reserve; when they fall back, the reserve returns to
 * retained earnings, as far as it was built. Equity to equity — never profit or loss.
 *
 * <p>Totals are portfolio-wide in the functional currency, from each open loan's last
 * measurement (the impairment job runs first). Posts on the last day of a month, or
 * whenever run manually; it moves the reserve to the target, so a rerun posts nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegulatoryReserveService {

    private static final String BALANCE_SQL =
            "SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0) "
            + "FROM journal_entries WHERE gl_account_id = ? AND currency_code = ?";

    private final LoanRepository loanRepository;
    private final GlAccountingService gl;
    private final FunctionalCurrency functionalCurrency;
    private final ExchangeRateService exchangeRateService;
    private final JournalEntryRepository journalEntryRepository;
    private final JdbcTemplate jdbcTemplate;

    /** Where the reserve stands: totals in the functional currency, and the move needed. */
    public record ReservePosition(String currency, BigDecimal prudentialProvisions, BigDecimal lossAllowance,
                                  BigDecimal target, BigDecimal reserveBalance, BigDecimal adjustment) {}

    /** One run: the position and the journal posted, if any; {@code skipped} = not a month end. */
    public record ReserveRun(LocalDate businessDate, boolean skipped, ReservePosition position, String transactionId) {}

    /** The position now, without posting. */
    @Transactional(readOnly = true)
    public ReservePosition position() {
        String functional = functionalCurrency.get();
        BigDecimal prudential = BigDecimal.ZERO, allowance = BigDecimal.ZERO;
        // Rows: [currency, stage, loans, principal, loss allowance, prudential provision]
        for (Object[] row : loanRepository.provisionTotals(EnumSet.of(LoanStatus.ACTIVE, LoanStatus.IN_ARREARS))) {
            String ccy = (String) row[0];
            allowance = allowance.add(toFunctional((BigDecimal) row[4], ccy, functional));
            prudential = prudential.add(toFunctional((BigDecimal) row[5], ccy, functional));
        }
        BigDecimal target = prudential.subtract(allowance).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal balance = reserveBalance(functional);
        return new ReservePosition(functional, prudential, allowance, target, balance, target.subtract(balance));
    }

    /**
     * Moves the reserve to its target. Skipped unless {@code manual} or the business date
     * is the last day of its month.
     */
    @Transactional
    public ReserveRun post(LocalDate businessDate, boolean manual) {
        if (!manual && businessDate.getDayOfMonth() != businessDate.lengthOfMonth()) {
            return new ReserveRun(businessDate, true, null, null);
        }
        // Serialises a manual run with the nightly one: the second sees the first's journal.
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> { }, "regulatory-risk-reserve");
        // The reserve balance is read with JDBC, which doesn't flush Hibernate's pending inserts.
        journalEntryRepository.flush();
        ReservePosition p = position();
        String transactionId = null;
        if (p.adjustment().signum() != 0) {
            GlAccount retained = gl.activityAccount(FinancialActivity.EQUITY_RETAINED_EARNINGS);
            GlAccount reserve = gl.activityAccount(FinancialActivity.EQUITY_REGULATORY_RISK_RESERVE);
            BigDecimal amount = p.adjustment().abs();
            List<JournalLine> lines = new ArrayList<>();
            if (p.adjustment().signum() > 0) {
                lines.add(JournalLine.debit(retained, amount, p.currency()));
                lines.add(JournalLine.credit(reserve, amount, p.currency()));
            } else {
                lines.add(JournalLine.debit(reserve, amount, p.currency()));
                lines.add(JournalLine.credit(retained, amount, p.currency()));
            }
            transactionId = gl.postJournal(lines, businessDate,
                    "Regulatory risk reserve (CBN): prudential provisions " + p.prudentialProvisions()
                            + " vs IFRS 9 allowance " + p.lossAllowance(),
                    JournalEntry.EntityType.REGULATORY_RESERVE, null, "RRR-" + businessDate);
            log.info("Regulatory risk reserve moved by {} {} to {}", p.adjustment(), p.currency(), p.target());
        }
        return new ReserveRun(businessDate, false, p, transactionId);
    }

    private BigDecimal toFunctional(BigDecimal amount, String currency, String functional) {
        if (amount == null || amount.signum() == 0) return BigDecimal.ZERO;
        if (currency.equalsIgnoreCase(functional)) return amount;
        return exchangeRateService.convert(amount, currency, functional).convertedAmount();
    }

    private BigDecimal reserveBalance(String functional) {
        GlAccount reserve = gl.activityAccount(FinancialActivity.EQUITY_REGULATORY_RISK_RESERVE);
        BigDecimal balance = jdbcTemplate.queryForObject(BALANCE_SQL, BigDecimal.class, reserve.getId(), functional);
        return balance == null ? BigDecimal.ZERO : balance;
    }
}

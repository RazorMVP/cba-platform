package com.cba.accounting;

import com.cba.accounting.FinancialActivityAccount.FinancialActivity;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The one-time opening-balance journal: brings customer balances that pre-date GL
 * posting into the ledger, exactly as they are (ISA 510 — no accrued interest or other
 * new income is booked on migration day; a legacy accrual gap is an IAS 8 retrospective
 * correction, not today's profit or loss).
 *
 * <p>For each control account and currency it posts the customer balances minus what the
 * ledger already holds — balances moved since GL posting went live are already there —
 * against the migration clearing account in the same currency. The rest of the legacy
 * trial balance is loaded against that clearing account as manual journals; once
 * migration is complete it must be zero in every currency.
 *
 * <p>Posted once per scope. After that, {@link #preview} is the reconciliation check:
 * any remaining difference between a control account and its customer balances is a
 * ledger break.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpeningBalanceService {

    /** Which sub-ledger to bring forward. Term deposits and shares follow with their GL posting. */
    public enum Scope { DEPOSITS, LOANS }

    /**
     * Customer balances vs ledger, per control account and currency, in one statement so
     * the two sides are read from the same snapshot. Balances are signed debit-positive:
     * a deposit (credit) is negative, an overdraft (debit) positive, so both are
     * {@code -SUM(balance)}. Resolution mirrors posting: the product's own GL link, else
     * the activity mapping (savings default, overdraft default — passed twice).
     */
    private static final String DEPOSITS_SQL = """
            WITH control_gls AS (
                SELECT savings_control_account_id AS gl_id FROM deposit_products
                WHERE savings_control_account_id IS NOT NULL
                UNION SELECT overdraft_portfolio_control_account_id FROM deposit_products
                WHERE overdraft_portfolio_control_account_id IS NOT NULL
                UNION SELECT CAST(? AS uuid)
                UNION SELECT CAST(? AS uuid)
            ),
            customer AS (
                SELECT COALESCE(dp.savings_control_account_id, CAST(? AS uuid)) AS gl_id,
                       a.currency_code AS ccy, -SUM(a.balance) AS net
                FROM accounts a LEFT JOIN deposit_products dp ON dp.id = a.product_id
                WHERE a.balance > 0
                GROUP BY 1, 2
                UNION ALL
                SELECT COALESCE(dp.overdraft_portfolio_control_account_id, CAST(? AS uuid)),
                       a.currency_code, -SUM(a.balance)
                FROM accounts a LEFT JOIN deposit_products dp ON dp.id = a.product_id
                WHERE a.balance < 0
                GROUP BY 1, 2
            ),
            target AS (
                SELECT gl_id, ccy, SUM(net) AS net FROM customer GROUP BY gl_id, ccy
            ),
            ledger AS (
                SELECT je.gl_account_id AS gl_id, je.currency_code AS ccy,
                       SUM(CASE WHEN je.entry_type = 'DEBIT' THEN je.amount ELSE -je.amount END) AS net
                FROM journal_entries je
                WHERE je.gl_account_id IN (SELECT gl_id FROM control_gls WHERE gl_id IS NOT NULL)
                GROUP BY je.gl_account_id, je.currency_code
            )
            SELECT COALESCE(t.gl_id, l.gl_id) AS gl_id, COALESCE(t.ccy, l.ccy) AS ccy,
                   COALESCE(t.net, 0) AS customer_net, COALESCE(l.net, 0) AS ledger_net
            FROM target t FULL OUTER JOIN ledger l ON l.gl_id = t.gl_id AND l.ccy = t.ccy
            """;

    /**
     * Open loans (ACTIVE, IN_ARREARS) vs ledger, per control account and currency (the
     * loan product's), debit-positive: principal on the portfolio (the product's link,
     * else the mapping); interest accrued less received and waived; recognised unpaid
     * charges; and, as credits, the deferred origination fee, interest and charges in
     * suspense, the loss allowance and the modification discount. Written-off loans are
     * left out: their schedule still shows the legal debt, the ledger nothing. Each
     * customer row names its activity, so an unmapped one can be reported by name.
     */
    private static final String LOANS_SQL = """
            WITH p AS (
                SELECT CAST(? AS uuid) AS portfolio, CAST(? AS uuid) AS interest, CAST(? AS uuid) AS fees,
                       CAST(? AS uuid) AS deferred_fees, CAST(? AS uuid) AS suspense,
                       CAST(? AS uuid) AS allowance, CAST(? AS uuid) AS modification
            ),
            open_loans AS (
                SELECT l.id, lp.currency_code AS ccy, COALESCE(lp.loan_portfolio_account_id, p.portfolio) AS portfolio_gl,
                       l.outstanding_balance, l.deferred_fee_remaining, l.interest_in_suspense,
                       l.loss_allowance, l.modification_discount_remaining
                FROM loans l JOIN loan_products lp ON lp.id = l.product_id CROSS JOIN p
                WHERE l.status IN ('ACTIVE', 'IN_ARREARS')
            ),
            recognised AS (
                SELECT ol.ccy, c.amount_outstanding, c.suspended
                FROM open_loans ol JOIN loan_charges c ON c.loan_id = ol.id
                WHERE c.income_recognized_on IS NOT NULL AND NOT c.paid AND NOT c.waived
            ),
            control_gls AS (
                SELECT loan_portfolio_account_id AS gl_id FROM loan_products WHERE loan_portfolio_account_id IS NOT NULL
                UNION SELECT unnest(ARRAY[portfolio, interest, fees, deferred_fees, suspense, allowance, modification]) FROM p
            ),
            customer AS (
                SELECT 'ASSET_LOAN_PORTFOLIO' AS act, portfolio_gl AS gl_id, ccy, SUM(outstanding_balance) AS net
                FROM open_loans GROUP BY 2, 3
                UNION ALL
                SELECT 'ASSET_INTEREST_RECEIVABLE', p.interest, ol.ccy,
                       SUM(s.interest_accrued - s.interest_paid - s.interest_waived)
                FROM open_loans ol JOIN loan_repayment_schedule s ON s.loan_id = ol.id CROSS JOIN p GROUP BY 2, 3
                UNION ALL
                SELECT 'ASSET_FEES_RECEIVABLE', p.fees, r.ccy, SUM(r.amount_outstanding)
                FROM recognised r CROSS JOIN p GROUP BY 2, 3
                UNION ALL
                SELECT 'ASSET_DEFERRED_LOAN_FEES', p.deferred_fees, ol.ccy, -SUM(ol.deferred_fee_remaining)
                FROM open_loans ol CROSS JOIN p GROUP BY 2, 3
                UNION ALL
                SELECT 'LIABILITY_INCOME_IN_SUSPENSE', p.suspense, ol.ccy, -SUM(ol.interest_in_suspense)
                FROM open_loans ol CROSS JOIN p GROUP BY 2, 3
                UNION ALL
                SELECT 'LIABILITY_INCOME_IN_SUSPENSE', p.suspense, r.ccy, -SUM(r.amount_outstanding)
                FROM recognised r CROSS JOIN p WHERE r.suspended GROUP BY 2, 3
                UNION ALL
                SELECT 'ASSET_LOAN_LOSS_ALLOWANCE', p.allowance, ol.ccy, -SUM(ol.loss_allowance)
                FROM open_loans ol CROSS JOIN p GROUP BY 2, 3
                UNION ALL
                SELECT 'ASSET_LOAN_MODIFICATION_DISCOUNT', p.modification, ol.ccy, -SUM(ol.modification_discount_remaining)
                FROM open_loans ol CROSS JOIN p GROUP BY 2, 3
            ),
            target AS (
                SELECT gl_id, CASE WHEN gl_id IS NULL THEN act END AS act, ccy, SUM(net) AS net
                FROM customer GROUP BY 1, 2, 3
            ),
            ledger AS (
                SELECT je.gl_account_id AS gl_id, je.currency_code AS ccy,
                       SUM(CASE WHEN je.entry_type = 'DEBIT' THEN je.amount ELSE -je.amount END) AS net
                FROM journal_entries je
                WHERE je.gl_account_id IN (SELECT gl_id FROM control_gls WHERE gl_id IS NOT NULL)
                GROUP BY je.gl_account_id, je.currency_code
            )
            SELECT COALESCE(t.gl_id, l.gl_id) AS gl_id, t.act, COALESCE(t.ccy, l.ccy) AS ccy,
                   COALESCE(t.net, 0) AS customer_net, COALESCE(l.net, 0) AS ledger_net
            FROM target t FULL OUTER JOIN ledger l ON l.gl_id = t.gl_id AND l.ccy = t.ccy
            """;

    /** The loan control accounts, in the order {@link #LOANS_SQL} binds them. */
    private static final List<FinancialActivity> LOAN_ACTIVITIES = List.of(
            FinancialActivity.ASSET_LOAN_PORTFOLIO, FinancialActivity.ASSET_INTEREST_RECEIVABLE,
            FinancialActivity.ASSET_FEES_RECEIVABLE, FinancialActivity.ASSET_DEFERRED_LOAN_FEES,
            FinancialActivity.LIABILITY_INCOME_IN_SUSPENSE, FinancialActivity.ASSET_LOAN_LOSS_ALLOWANCE,
            FinancialActivity.ASSET_LOAN_MODIFICATION_DISCOUNT);

    private static final String CLEARING_SQL = """
            SELECT currency_code AS ccy,
                   SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) AS net
            FROM journal_entries WHERE gl_account_id = ?
            GROUP BY currency_code
            """;

    private final GlAccountingService gl;
    private final GlAccountRepository glAccountRepository;
    private final FinancialActivityAccountRepository financialActivityRepo;
    private final JournalEntryRepository journalEntryRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * One control account in one currency. Balances are debit-positive (a deposit
     * control's credit balance is negative); {@code openingAmount} is what the journal
     * posts to it: {@code customerBalance - ledgerBalance}.
     */
    public record OpeningLine(UUID glAccountId, String glCode, String glName, String currency,
                              BigDecimal customerBalance, BigDecimal ledgerBalance, BigDecimal openingAmount) {}

    /**
     * The journal lines (to post, or still unreconciled once posted) and the clearing
     * account's balance per currency (debit-positive), which must reach zero.
     */
    public record OpeningBalances(Scope scope, LocalDate date, boolean posted, String transactionId,
                                  List<OpeningLine> lines, String clearingGlCode,
                                  Map<String, BigDecimal> clearingBalance, boolean clearingIsZero) {}

    @Transactional(readOnly = true)
    public OpeningBalances preview(Scope scope) {
        GlAccount clearing = gl.activityAccount(FinancialActivity.EQUITY_MIGRATION_CLEARING);
        String existing = postedTransactionId(scope);
        return result(scope, existing != null, existing, compute(scope), clearing);
    }

    /** Posts the journal once; a second call for the same scope is rejected. */
    @Transactional
    public OpeningBalances post(Scope scope) {
        // Serialises concurrent posts: the second waits here, then sees the first's journal.
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> { },
                "opening-balance-" + scope);
        if (postedTransactionId(scope) != null) {
            throw CbaException.conflict("OPENING_BALANCES_ALREADY_POSTED",
                    "The opening-balance journal for " + scope + " has already been posted");
        }
        GlAccount clearing = gl.activityAccount(FinancialActivity.EQUITY_MIGRATION_CLEARING);
        List<OpeningLine> lines = compute(scope);

        List<JournalLine> journal = new ArrayList<>();
        Map<String, BigDecimal> offsetByCurrency = new TreeMap<>();
        for (OpeningLine line : lines) {
            if (line.openingAmount().signum() == 0) continue;
            GlAccount account = glAccountRepository.findById(line.glAccountId()).orElseThrow();
            journal.add(signed(account, line.openingAmount(), line.currency()));
            offsetByCurrency.merge(line.currency(), line.openingAmount(), BigDecimal::add);
        }
        String transactionId = null;
        if (!journal.isEmpty()) {
            offsetByCurrency.forEach((ccy, net) -> journal.add(signed(clearing, net.negate(), ccy)));
            transactionId = gl.postJournal(journal, LocalDate.now(),
                    "Opening balances brought forward: " + scope.name().toLowerCase(java.util.Locale.ROOT)
                            + " (ISA 510)",
                    JournalEntry.EntityType.OPENING_BALANCE, null, reference(scope));
            log.info("Opening-balance journal {} posted for {}: {} lines", transactionId, scope, journal.size());
            // The clearing balance below is read with JDBC, which doesn't flush Hibernate's pending inserts.
            journalEntryRepository.flush();
        }
        return result(scope, transactionId != null, transactionId, lines, clearing);
    }

    private List<OpeningLine> compute(Scope scope) {
        List<OpeningLine> lines = scope == Scope.LOANS ? computeLoans() : computeDeposits();
        lines.sort(Comparator.comparing(OpeningLine::glCode).thenComparing(OpeningLine::currency));
        return lines;
    }

    private List<OpeningLine> computeDeposits() {
        UUID savingsDefault = mapped(FinancialActivity.LIABILITY_SAVINGS_CONTROL);
        UUID overdraftDefault = mapped(FinancialActivity.ASSET_OVERDRAFT_PORTFOLIO);
        List<OpeningLine> lines = new ArrayList<>();
        jdbcTemplate.query(DEPOSITS_SQL, rs -> {
            String glId = rs.getString("gl_id");
            String ccy = rs.getString("ccy");
            if (glId == null) {
                throw CbaException.badRequest("ACTIVITY_NOT_MAPPED",
                        "Deposit balances in " + ccy + " have no control GL account: map "
                                + FinancialActivity.LIABILITY_SAVINGS_CONTROL + " and "
                                + FinancialActivity.ASSET_OVERDRAFT_PORTFOLIO + " or link them on the product");
            }
            BigDecimal customer = rs.getBigDecimal("customer_net");
            BigDecimal ledger = rs.getBigDecimal("ledger_net");
            GlAccount account = glAccountRepository.findById(UUID.fromString(glId)).orElseThrow();
            lines.add(new OpeningLine(account.getId(), account.getGlCode(), account.getName(), ccy,
                    customer, ledger, customer.subtract(ledger)));
        }, savingsDefault, overdraftDefault, savingsDefault, overdraftDefault);
        return lines;
    }

    private List<OpeningLine> computeLoans() {
        List<OpeningLine> lines = new ArrayList<>();
        jdbcTemplate.query(LOANS_SQL, rs -> {
            String glId = rs.getString("gl_id");
            String ccy = rs.getString("ccy");
            BigDecimal customer = rs.getBigDecimal("customer_net");
            if (glId == null) {
                // An activity not mapped is fine while nothing is owed on it (e.g. no waivers yet).
                if (customer.signum() == 0) return;
                throw CbaException.badRequest("ACTIVITY_NOT_MAPPED", "Loan balances of " + customer + " " + ccy
                        + " have no GL account: map " + rs.getString("act"));
            }
            BigDecimal ledger = rs.getBigDecimal("ledger_net");
            GlAccount account = glAccountRepository.findById(UUID.fromString(glId)).orElseThrow();
            lines.add(new OpeningLine(account.getId(), account.getGlCode(), account.getName(), ccy,
                    customer, ledger, customer.subtract(ledger)));
        }, LOAN_ACTIVITIES.stream().map(this::mapped).toArray());
        return lines;
    }

    private OpeningBalances result(Scope scope, boolean posted, String transactionId,
                                   List<OpeningLine> lines, GlAccount clearing) {
        Map<String, BigDecimal> clearingBalance = new TreeMap<>();
        jdbcTemplate.query(CLEARING_SQL, rs -> {
            clearingBalance.put(rs.getString("ccy"), rs.getBigDecimal("net"));
        }, clearing.getId());
        boolean zero = clearingBalance.values().stream().allMatch(v -> v.signum() == 0);
        return new OpeningBalances(scope, LocalDate.now(), posted, transactionId, lines,
                clearing.getGlCode(), clearingBalance, zero);
    }

    private String postedTransactionId(Scope scope) {
        return journalEntryRepository
                .findFirstByEntityTypeAndReferenceNumber(JournalEntry.EntityType.OPENING_BALANCE, reference(scope))
                .map(JournalEntry::getTransactionId).orElse(null);
    }

    private UUID mapped(FinancialActivity activity) {
        return financialActivityRepo.findByFinancialActivity(activity)
                .map(f -> f.getGlAccount().getId()).orElse(null);
    }

    private static String reference(Scope scope) {
        return "OPENING-" + scope.name();
    }

    /** A debit-positive amount as a journal line. */
    private static JournalLine signed(GlAccount account, BigDecimal debitPositive, String currency) {
        return debitPositive.signum() > 0
                ? JournalLine.debit(account, debitPositive, currency)
                : JournalLine.credit(account, debitPositive.negate(), currency);
    }
}

package com.cba.integration;

import com.cba.common.exception.CbaException;
import com.cba.loan.LoanAccrualService;
import com.cba.loan.LoanImpairmentService;
import com.cba.loan.LoanService;
import com.cba.loan.LoanStatus;
import com.cba.loan.RegulatoryReserveService;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanRepaymentRequest;
import com.cba.loan.dto.WriteOffRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IFRS 9 impairment, write-off, recovery and the CBN regulatory risk reserve against a
 * real database. Each loan is moved 400 days into the past after disbursement, so it is
 * long overdue (stage 3, CBN "lost") on today's business date.
 */
@DisplayName("Loan impairment — ECL allowance, write-off, recovery and the CBN reserve against a real database")
class LoanImpairmentIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID USD_LOAN_PRODUCT = UUID.fromString("20000000-0000-0000-0000-000000000001"); // fee 50.00
    static final List<String> LOAN_ASSETS = List.of("1100", "1101", "1103", "1104", "2200", "1105");

    @Autowired LoanService loanService;
    @Autowired LoanAccrualService loanAccrualService;
    @Autowired LoanImpairmentService loanImpairmentService;
    @Autowired RegulatoryReserveService regulatoryReserveService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("stage 3 allowance → CBN rule blocks → write-off against the allowance → recovery is a gain → no undo")
    void impairment_writeOff_recovery() {
        UUID account = account();
        UUID loanId = overdueLoan(account);
        LocalDate today = LocalDate.now();

        // 1. Close of business: accrual (non-performing, interest to suspense), then impairment.
        loanAccrualService.runCloseOfBusiness(loanId, today);
        loanImpairmentService.measure(loanId, today);
        assertThat(loanInt(loanId, "ecl_stage")).isEqualTo(3);
        BigDecimal allowance = loanColumn(loanId, "loss_allowance");
        // EAD 1200 (accrued interest sits in suspense; the fee is fully released) × PD 100% × LGD 45%.
        assertThat(allowance).isEqualByComparingTo("540.00");
        assertThat(netCredit(loanId, "1105")).isEqualByComparingTo(allowance);
        assertThat(netDebit(loanId, "5004")).isEqualByComparingTo(allowance);
        assertThat(loanColumn(loanId, "prudential_provision")).as("CBN lost: 100% of principal").isEqualByComparingTo("1200.00");
        assertThat(jdbc.queryForObject("SELECT fully_provided_since FROM loans WHERE id = ?", LocalDate.class, loanId))
                .isEqualTo(today);

        // 2. CBN: fully provided only today, not for a year — the write-off is refused.
        assertThatThrownBy(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(null, "uncollectable", "BRD/IT/1")))
                .isInstanceOf(CbaException.class).hasMessageContaining("fully provided since");

        // 3. With the rule switched off: written off against the allowance; every loan account nets to zero.
        withCbnWriteOffRuleOff(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(null, "uncollectable", "BRD/IT/1")));
        assertThat(loanStatus(loanId)).isEqualTo(LoanStatus.WRITTEN_OFF.name());
        BigDecimal writtenOff = loanColumn(loanId, "written_off_amount");
        assertThat(writtenOff).isEqualByComparingTo("1200.00");
        LOAN_ASSETS.forEach(gl -> assertThat(netDebit(loanId, gl)).as("net " + gl).isEqualByComparingTo("0"));
        assertThat(netDebit(loanId, "5004")).as("impairment loss = the amount written off").isEqualByComparingTo(writtenOff);

        // 4. Cash recovered later is an impairment gain; the account pays it.
        BigDecimal before = balance(account);
        loanService.makeRepayment(loanId, new LoanRepaymentRequest(new BigDecimal("100.00"), null, null, null, null));
        assertThat(balance(account)).isEqualByComparingTo(before.subtract(new BigDecimal("100.00")));
        assertThat(loanColumn(loanId, "recovered_amount")).isEqualByComparingTo("100.00");
        assertThat(netDebit(loanId, "5004")).isEqualByComparingTo(writtenOff.subtract(new BigDecimal("100.00")));

        // 5. Once cash has been recovered, the write-off stands.
        assertThatThrownBy(() -> loanService.undoWriteOff(loanId))
                .isInstanceOf(CbaException.class).hasMessageContaining("recovered");
        assertBalancedPerJournal(loanId);
    }

    @Test
    @DisplayName("undo write-off: the reversal brings every GL balance and the loan back")
    void undoWriteOff_restoresBalances() {
        UUID loanId = overdueLoan(account());
        LocalDate today = LocalDate.now();
        loanAccrualService.runCloseOfBusiness(loanId, today);
        loanImpairmentService.measure(loanId, today);
        Map<String, BigDecimal> balances = new TreeMap<>();
        LOAN_ASSETS.forEach(gl -> balances.put(gl, netDebit(loanId, gl)));
        BigDecimal outstanding = loanColumn(loanId, "outstanding_balance");

        withCbnWriteOffRuleOff(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(null, "error", "BRD/IT/2")));
        loanService.undoWriteOff(loanId);

        assertThat(loanStatus(loanId)).isEqualTo(LoanStatus.IN_ARREARS.name());
        assertThat(loanColumn(loanId, "outstanding_balance")).isEqualByComparingTo(outstanding);
        assertThat(loanColumn(loanId, "loss_allowance")).isEqualByComparingTo(balances.get("1105").negate());
        balances.forEach((gl, b) -> assertThat(netDebit(loanId, gl)).as("net " + gl).isEqualByComparingTo(b));
        assertBalancedPerJournal(loanId);
    }

    @Test
    @DisplayName("regulatory risk reserve: retained earnings → reserve up to prudential − ECL; a rerun posts nothing")
    void regulatoryReserve() {
        UUID loanId = overdueLoan(account());
        LocalDate today = LocalDate.now();
        loanAccrualService.runCloseOfBusiness(loanId, today);
        loanImpairmentService.measure(loanId, today);

        RegulatoryReserveService.ReserveRun run = regulatoryReserveService.post(today, true);

        RegulatoryReserveService.ReservePosition p = run.position();
        assertThat(p.target()).isEqualByComparingTo(p.prudentialProvisions().subtract(p.lossAllowance()).max(BigDecimal.ZERO));
        assertThat(glCredit("3002", p.currency())).isEqualByComparingTo(p.target());
        assertThat(regulatoryReserveService.post(today, true).transactionId()).isNull();
        assertThat(regulatoryReserveService.post(LocalDate.of(2026, 10, 30), false).skipped()).isTrue();
    }

    @Test
    @DisplayName("no GL mapping for the impairment charge: the measurement fails and posts nothing")
    void impairment_withoutMapping_fails() {
        UUID loanId = overdueLoan(account());
        LocalDate today = LocalDate.now();
        loanAccrualService.runCloseOfBusiness(loanId, today);
        UUID gl = jdbc.queryForObject("SELECT gl_account_id FROM financial_activity_accounts "
                + "WHERE financial_activity = 'EXPENSE_LOAN_LOSS_PROVISION'", UUID.class);
        jdbc.update("DELETE FROM financial_activity_accounts WHERE financial_activity = 'EXPENSE_LOAN_LOSS_PROVISION'");
        try {
            assertThatThrownBy(() -> loanImpairmentService.measure(loanId, today))
                    .isInstanceOf(CbaException.class).hasMessageContaining("EXPENSE_LOAN_LOSS_PROVISION");
            assertThat(loanColumn(loanId, "loss_allowance")).isEqualByComparingTo("0");
            assertThat(netDebit(loanId, "1105")).isEqualByComparingTo("0");
        } finally {
            jdbc.update("INSERT INTO financial_activity_accounts (financial_activity, gl_account_id) "
                    + "VALUES ('EXPENSE_LOAN_LOSS_PROVISION', ?)", gl);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A 1200 loan disbursed today, then moved 400 days back: every instalment is past due. */
    private UUID overdueLoan(UUID account) {
        UUID loanId = loanService.applyForLoan(new LoanApplicationRequest(
                DEMO_CUSTOMER, USD_LOAN_PRODUCT, account, new BigDecimal("1200.00"), 12, "impairment-it")).id();
        loanService.approveLoan(loanId, "it");
        loanService.disburseLoan(loanId);
        jdbc.update("""
                UPDATE loans SET disbursement_date = disbursement_date - 400,
                                 interest_accrued_through = interest_accrued_through - 400,
                                 maturity_date = maturity_date - 400
                WHERE id = ?""", loanId);
        jdbc.update("UPDATE loan_repayment_schedule SET due_date = due_date - 400 WHERE loan_id = ?", loanId);
        return loanId;
    }

    private void withCbnWriteOffRuleOff(Runnable action) {
        jdbc.update("UPDATE global_configurations SET boolean_value = FALSE WHERE name = 'enforce-cbn-write-off-rules'");
        try {
            action.run();
        } finally {
            jdbc.update("UPDATE global_configurations SET boolean_value = TRUE WHERE name = 'enforce-cbn-write-off-rules'");
        }
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', 5000.00, 'USD', DATE '2024-01-01', 'loan-impairment-it')""",
                id, "LN-ECL-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT);
        return id;
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    private String loanStatus(UUID loanId) {
        return jdbc.queryForObject("SELECT status FROM loans WHERE id = ?", String.class, loanId);
    }

    private BigDecimal loanColumn(UUID loanId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM loans WHERE id = ?", BigDecimal.class, loanId);
    }

    private int loanInt(UUID loanId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM loans WHERE id = ?", Integer.class, loanId);
    }

    /** Debits minus credits on one GL code for the loan. */
    private BigDecimal netDebit(UUID loanId, String glCode) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'DEBIT' THEN je.amount ELSE -je.amount END), 0)
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_id = ? AND g.gl_code = ?""", BigDecimal.class, loanId, glCode);
    }

    private BigDecimal netCredit(UUID loanId, String glCode) {
        return netDebit(loanId, glCode).negate();
    }

    /** Credits minus debits on one GL code across the whole ledger, in one currency. */
    private BigDecimal glCredit(String glCode, String currency) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'CREDIT' THEN je.amount ELSE -je.amount END), 0)
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE g.gl_code = ? AND je.currency_code = ?""", BigDecimal.class, glCode, currency);
    }

    private void assertBalancedPerJournal(UUID loanId) {
        Map<String, BigDecimal> net = new TreeMap<>();
        jdbc.queryForList("SELECT transaction_id, entry_type, amount, currency_code FROM journal_entries WHERE entity_id = ?",
                loanId).forEach(row -> net.merge(row.get("transaction_id") + "/" + row.get("currency_code"),
                "DEBIT".equals(row.get("entry_type")) ? (BigDecimal) row.get("amount")
                        : ((BigDecimal) row.get("amount")).negate(), BigDecimal::add));
        net.forEach((k, n) -> assertThat(n).as("net " + k).isEqualByComparingTo("0"));
    }
}

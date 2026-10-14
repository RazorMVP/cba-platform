package com.cba.integration;

import com.cba.loan.LoanAccrualService;
import com.cba.loan.LoanImpairmentService;
import com.cba.loan.LoanService;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanRepaymentRequest;
import com.cba.loan.dto.WaiveInterestRequest;
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

/**
 * Interest waivers against a real database (IFRS 9 §3.2.3 for accrued interest, §5.4.3
 * for future interest). Loans are disbursed today and moved back in time, so interest has
 * accrued and instalments have fallen due on today's business date.
 */
@DisplayName("Loan interest waivers — derecognition, modification loss and its unwind against a real database")
class LoanWaiverIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID USD_LOAN_PRODUCT = UUID.fromString("20000000-0000-0000-0000-000000000001"); // fee 50.00
    static final List<String> LOAN_ACCOUNTS = List.of("1100", "1101", "1103", "1104", "1105", "1106", "2200");

    @Autowired LoanService loanService;
    @Autowired LoanAccrualService loanAccrualService;
    @Autowired LoanImpairmentService loanImpairmentService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("performing, ALL: accrued interest derecognised, future interest a modification loss; payoff clears 1106")
    void performing_waiveAll_thenPayOff() {
        UUID loanId = loan(account(), 40); // first instalment ~9 days overdue: performing
        LocalDate today = LocalDate.now();
        loanAccrualService.runCloseOfBusiness(loanId, today);
        BigDecimal accrued = netDebit(loanId, "1101");
        assertThat(accrued).isPositive();

        loanService.waiveInterest(loanId, new WaiveInterestRequest("hardship", WaiveInterestRequest.Scope.ALL, null, "CC/IT/1"));

        BigDecimal discount = loanColumn(loanId, "modification_discount_remaining");
        assertThat(discount).as("PV of the future interest given up").isPositive();
        assertThat(netDebit(loanId, "1101")).as("accrued interest derecognised").isEqualByComparingTo("0");
        assertThat(netCredit(loanId, "1106")).isEqualByComparingTo(discount);
        assertThat(netDebit(loanId, "5005")).as("loss: accrued + modification").isEqualByComparingTo(accrued.add(discount));
        assertThat(jdbc.queryForObject("SELECT SUM(interest_due) FROM loan_repayment_schedule WHERE loan_id = ?",
                BigDecimal.class, loanId)).as("only what had accrued is left due").isEqualByComparingTo(accrued);
        assertThat(jdbc.queryForObject("SELECT SUM(modification_unwind) FROM loan_repayment_schedule WHERE loan_id = ?",
                BigDecimal.class, loanId)).as("the discount is spread exactly").isEqualByComparingTo(discount);

        // Paying off the principal settles the loan: the discount not yet unwound is income now.
        loanService.makeRepayment(loanId, new LoanRepaymentRequest(
                loanColumn(loanId, "outstanding_balance"), null, null, null, null));
        assertThat(loanStatus(loanId)).isEqualTo("CLOSED_OBLIGATIONS_MET");
        LOAN_ACCOUNTS.forEach(gl -> assertThat(netDebit(loanId, gl)).as("net " + gl).isEqualByComparingTo("0"));
        assertBalancedPerJournal(loanId);
    }

    @Test
    @DisplayName("ACCRUED: only accrued interest goes; future interest still accrues")
    void waiveAccruedOnly() {
        UUID loanId = loan(account(), 40);
        LocalDate today = LocalDate.now();
        loanAccrualService.runCloseOfBusiness(loanId, today);
        BigDecimal accrued = netDebit(loanId, "1101");
        BigDecimal interestDue = sumSchedule(loanId, "interest_due");

        loanService.waiveInterest(loanId, new WaiveInterestRequest("goodwill", WaiveInterestRequest.Scope.ACCRUED, null, null));

        assertThat(netDebit(loanId, "1101")).isEqualByComparingTo("0");
        assertThat(netDebit(loanId, "5005")).isEqualByComparingTo(accrued);
        assertThat(netDebit(loanId, "1106")).isEqualByComparingTo("0");
        assertThat(sumSchedule(loanId, "interest_due")).isEqualByComparingTo(interestDue);
        assertThat(sumSchedule(loanId, "interest_waived")).isEqualByComparingTo(accrued);

        // Interest keeps accruing on the rest of the term.
        loanAccrualService.runCloseOfBusiness(loanId, today.plusDays(10));
        assertThat(netDebit(loanId, "1101")).isPositive();
        assertBalancedPerJournal(loanId);
    }

    @Test
    @DisplayName("the discount unwinds into income by the last due date: Σ unwind journals = the loss")
    void discountUnwindsToMaturity() {
        UUID loanId = loan(account(), 40);
        LocalDate today = LocalDate.now();
        loanAccrualService.runCloseOfBusiness(loanId, today);
        loanService.waiveInterest(loanId, new WaiveInterestRequest("hardship", WaiveInterestRequest.Scope.ALL, null, null));
        BigDecimal discount = loanColumn(loanId, "modification_discount_remaining");

        loanAccrualService.runCloseOfBusiness(loanId, today.plusYears(1)); // past maturity

        assertThat(netDebit(loanId, "1106")).as("fully unwound").isEqualByComparingTo("0");
        assertThat(loanColumn(loanId, "modification_discount_remaining")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(je.amount), 0) FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_id = ? AND g.gl_code = '1106' AND je.entry_type = 'DEBIT'
                  AND je.reference_number LIKE 'MODU-%'""", BigDecimal.class, loanId)).isEqualByComparingTo(discount);
        assertBalancedPerJournal(loanId);
    }

    @Test
    @DisplayName("non-performing: the waived interest leaves suspense; no loss, no modification loss")
    void nonPerforming_fromSuspense() {
        UUID loanId = loan(account(), 130); // first instalment ~99 days overdue: non-performing
        LocalDate today = LocalDate.now();
        loanAccrualService.runCloseOfBusiness(loanId, today);
        BigDecimal suspense = netCredit(loanId, "2200");
        assertThat(suspense).isPositive();
        assertThat(netDebit(loanId, "1101")).isEqualByComparingTo(suspense);

        loanService.waiveInterest(loanId, new WaiveInterestRequest("restructure", WaiveInterestRequest.Scope.ALL, null, null));

        assertThat(netDebit(loanId, "1101")).isEqualByComparingTo("0");
        assertThat(netDebit(loanId, "2200")).isEqualByComparingTo("0");
        assertThat(netDebit(loanId, "5005")).isEqualByComparingTo("0");
        assertThat(netDebit(loanId, "1106")).isEqualByComparingTo("0");
        assertThat(loanColumn(loanId, "interest_in_suspense")).isEqualByComparingTo("0");
        assertBalancedPerJournal(loanId);
    }

    @Test
    @DisplayName("write-off derecognises the modification discount too; undo brings it back")
    void writeOff_andUndo_withDiscount() {
        UUID loanId = loan(account(), 40);
        LocalDate today = LocalDate.now();
        loanAccrualService.runCloseOfBusiness(loanId, today);
        loanService.waiveInterest(loanId, new WaiveInterestRequest("hardship", WaiveInterestRequest.Scope.ALL, null, null));
        // Three months later and unpaid: credit-impaired, with part of the discount still to unwind.
        shift(loanId, 90);
        loanAccrualService.runCloseOfBusiness(loanId, today);
        loanImpairmentService.measure(loanId, today);
        assertThat(netCredit(loanId, "1106")).isPositive();
        Map<String, BigDecimal> before = new TreeMap<>();
        LOAN_ACCOUNTS.forEach(gl -> before.put(gl, netDebit(loanId, gl)));

        withCbnWriteOffRuleOff(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(null, "uncollectable", "BRD/IT/W")));
        LOAN_ACCOUNTS.forEach(gl -> assertThat(netDebit(loanId, gl)).as("net " + gl).isEqualByComparingTo("0"));
        assertThat(loanColumn(loanId, "modification_discount_remaining")).isEqualByComparingTo("0");

        loanService.undoWriteOff(loanId);
        before.forEach((gl, b) -> assertThat(netDebit(loanId, gl)).as("net " + gl).isEqualByComparingTo(b));
        assertThat(loanColumn(loanId, "modification_discount_remaining")).isEqualByComparingTo(before.get("1106").negate());
        assertBalancedPerJournal(loanId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A 1200, 12-month loan disbursed today, then moved {@code days} back. */
    private UUID loan(UUID account, int days) {
        UUID loanId = loanService.applyForLoan(new LoanApplicationRequest(
                DEMO_CUSTOMER, USD_LOAN_PRODUCT, account, new BigDecimal("1200.00"), 12, "waiver-it")).id();
        loanService.approveLoan(loanId, "it");
        loanService.disburseLoan(loanId);
        shift(loanId, days);
        return loanId;
    }

    private void shift(UUID loanId, int days) {
        jdbc.update("""
                UPDATE loans SET disbursement_date = disbursement_date - ?,
                                 interest_accrued_through = interest_accrued_through - ?,
                                 maturity_date = maturity_date - ?
                WHERE id = ?""", days, days, days, loanId);
        jdbc.update("UPDATE loan_repayment_schedule SET due_date = due_date - ? WHERE loan_id = ?", days, loanId);
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
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', 5000.00, 'USD', DATE '2024-01-01', 'loan-waiver-it')""",
                id, "LN-IWV-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT);
        return id;
    }

    private String loanStatus(UUID loanId) {
        return jdbc.queryForObject("SELECT status FROM loans WHERE id = ?", String.class, loanId);
    }

    private BigDecimal loanColumn(UUID loanId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM loans WHERE id = ?", BigDecimal.class, loanId);
    }

    private BigDecimal sumSchedule(UUID loanId, String column) {
        return jdbc.queryForObject("SELECT SUM(" + column + ") FROM loan_repayment_schedule WHERE loan_id = ?",
                BigDecimal.class, loanId);
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

    private void assertBalancedPerJournal(UUID loanId) {
        Map<String, BigDecimal> net = new TreeMap<>();
        jdbc.queryForList("SELECT transaction_id, entry_type, amount, currency_code FROM journal_entries WHERE entity_id = ?",
                loanId).forEach(row -> net.merge(row.get("transaction_id") + "/" + row.get("currency_code"),
                "DEBIT".equals(row.get("entry_type")) ? (BigDecimal) row.get("amount")
                        : ((BigDecimal) row.get("amount")).negate(), BigDecimal::add));
        net.forEach((k, n) -> assertThat(n).as("net " + k).isEqualByComparingTo("0"));
    }
}

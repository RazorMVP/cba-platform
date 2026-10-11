package com.cba.integration;

import com.cba.loan.LoanAccrualService;
import com.cba.loan.LoanService;
import com.cba.loan.LoanStatus;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanRepaymentRequest;
import com.cba.loan.dto.LoanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loan income over a loan's life against a real database: the origination fee deducted
 * and deferred, daily accrual to income, the move to suspense at 90 days past due
 * (CBN), and settlement — after which every loan asset and the suspense account net to
 * zero, and the income recognised equals the scheduled interest plus the fee.
 */
@DisplayName("Loan accrual — interest, fee amortisation and non-performing suspense against a real database")
class LoanAccrualIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID USD_LOAN_PRODUCT = UUID.fromString("20000000-0000-0000-0000-000000000001"); // fee 50.00

    @Autowired LoanService loanService;
    @Autowired LoanAccrualService loanAccrualService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("disburse with fee → accrue → non-performing → repay in full: everything nets to zero")
    void loanLife_accrualSuspenseAndSettlement() {
        UUID account = account();
        LoanResponse applied = loanService.applyForLoan(new LoanApplicationRequest(
                DEMO_CUSTOMER, USD_LOAN_PRODUCT, account, new BigDecimal("1200.00"), 12, "accrual-it"));
        UUID loanId = applied.id();
        loanService.approveLoan(loanId, "it");
        loanService.disburseLoan(loanId);
        LocalDate today = LocalDate.now();
        LocalDate firstDue = today.plusMonths(1);

        // 1. Forty days on, performing: interest and fee amortisation go to income.
        loanAccrualService.runCloseOfBusiness(loanId, today.plusDays(40));
        BigDecimal accrued = sumSchedule(loanId, "interest_accrued");
        BigDecimal feeLeft = loanColumn(loanId, "deferred_fee_remaining");
        assertThat(accrued).isPositive();
        assertThat(feeLeft).isLessThan(new BigDecimal("50.00"));
        assertThat(netDebit(loanId, "1101")).isEqualByComparingTo(accrued);
        assertThat(netDebit(loanId, "1104")).isEqualByComparingTo(feeLeft.negate());
        assertThat(netCredit(loanId, "4001")).isEqualByComparingTo(accrued.add(new BigDecimal("50.00")).subtract(feeLeft));
        assertThat(loanInt(loanId, "days_past_due")).isPositive(); // the first instalment is overdue

        // 2. 95 days past the first due date: non-performing. Accrued unpaid interest leaves
        //    income for suspense; later accruals go straight there. The fee keeps releasing.
        loanAccrualService.runCloseOfBusiness(loanId, firstDue.plusDays(95));
        assertThat(jdbc.queryForObject("SELECT non_performing FROM loans WHERE id = ?", Boolean.class, loanId)).isTrue();
        BigDecimal suspense = loanColumn(loanId, "interest_in_suspense");
        assertThat(suspense).isEqualByComparingTo(sumSchedule(loanId, "interest_accrued"));
        assertThat(netCredit(loanId, "2200")).isEqualByComparingTo(suspense);
        assertThat(netCredit(loanId, "4001")).as("only the fee is income while non-performing")
                .isEqualByComparingTo(new BigDecimal("50.00").subtract(loanColumn(loanId, "deferred_fee_remaining")));

        // 3. Repay everything: interest received releases suspense to income; settlement
        //    recognises interest paid ahead of accrual and the rest of the fee.
        BigDecimal owed = jdbc.queryForObject("""
                SELECT SUM(principal_due + interest_due + fees_due - principal_paid - interest_paid - fees_paid)
                FROM loan_repayment_schedule WHERE loan_id = ?""", BigDecimal.class, loanId);
        loanService.makeRepayment(loanId, new LoanRepaymentRequest(owed, null, null, null, null));

        assertThat(jdbc.queryForObject("SELECT status FROM loans WHERE id = ?", String.class, loanId))
                .isEqualTo(LoanStatus.CLOSED_OBLIGATIONS_MET.name());
        assertThat(netDebit(loanId, "1100")).isEqualByComparingTo("0");
        assertThat(netDebit(loanId, "1101")).isEqualByComparingTo("0");
        assertThat(netDebit(loanId, "1104")).isEqualByComparingTo("0");
        assertThat(netCredit(loanId, "2200")).isEqualByComparingTo("0");
        assertThat(netCredit(loanId, "4001")).as("income = scheduled interest + origination fee")
                .isEqualByComparingTo(sumSchedule(loanId, "interest_due").add(new BigDecimal("50.00")));
        assertBalancedPerJournal(loanId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', 5000.00, 'USD', DATE '2025-01-01', 'loan-accrual-it')""",
                id, "LN-ACR-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT);
        return id;
    }

    private BigDecimal sumSchedule(UUID loanId, String column) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM loan_repayment_schedule WHERE loan_id = ?",
                BigDecimal.class, loanId);
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

    private void assertBalancedPerJournal(UUID loanId) {
        Map<String, BigDecimal> net = new TreeMap<>();
        jdbc.queryForList("SELECT transaction_id, entry_type, amount, currency_code FROM journal_entries WHERE entity_id = ?",
                loanId).forEach(row -> net.merge(row.get("transaction_id") + "/" + row.get("currency_code"),
                "DEBIT".equals(row.get("entry_type")) ? (BigDecimal) row.get("amount")
                        : ((BigDecimal) row.get("amount")).negate(), BigDecimal::add));
        net.forEach((k, n) -> assertThat(n).as("net " + k).isEqualByComparingTo("0"));
    }
}

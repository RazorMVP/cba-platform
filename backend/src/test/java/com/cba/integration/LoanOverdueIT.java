package com.cba.integration;

import com.cba.common.DashboardController;
import com.cba.loan.Loan;
import com.cba.loan.LoanAccrualService;
import com.cba.loan.LoanRepaymentScheduleRepository;
import com.cba.loan.LoanRepository;
import com.cba.loan.LoanService;
import com.cba.loan.LoanStatus;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.report.Report;
import com.cba.report.ReportRepository;
import com.cba.report.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Overdue is derived from the due date (nothing stores status OVERDUE). Before V61 the
 * dashboard overdue figures, the arrears buckets and the LoansInArrears report all
 * filtered on that status and returned nothing. Counts are compared before and after,
 * because other tests share the database.
 */
@DisplayName("Overdue instalments — dashboard, arrears buckets and LoansInArrears report against a real database")
class LoanOverdueIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID USD_LOAN_PRODUCT = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired LoanService loanService;
    @Autowired LoanRepository loanRepository;
    @Autowired LoanRepaymentScheduleRepository scheduleRepository;
    @Autowired ReportService reportService;
    @Autowired ReportRepository reportRepository;
    @Autowired DashboardController dashboard;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("unpaid and partly paid instalments past due count; paid ones and future ones don't")
    void overdueDerivedFromDueDate() {
        LocalDate today = LocalDate.now();
        long countBefore = scheduleRepository.countOverdue(today);
        BigDecimal balanceBefore = scheduleRepository.sumOverdueBalance(today);
        long[] bucketsBefore = buckets(today);

        UUID loanId = disbursedLoan();
        // Instalment 1 due 70 days ago, unpaid; 2 due 40 days ago, partly paid; 3 due
        // 10 days ago, paid; the rest still in the future.
        moveDue(loanId, 1, today.minusDays(70));
        moveDue(loanId, 2, today.minusDays(40));
        moveDue(loanId, 3, today.minusDays(10));
        jdbc.update("""
                UPDATE loan_repayment_schedule SET principal_paid = 10, total_paid = 10, status = 'PARTIALLY_PAID'
                WHERE loan_id = ? AND installment_no = 2""", loanId);
        jdbc.update("""
                UPDATE loan_repayment_schedule SET principal_paid = principal_due, interest_paid = interest_due,
                       total_paid = total_due, status = 'PAID', paid_date = ?
                WHERE loan_id = ? AND installment_no = 3""", today.minusDays(10), loanId);
        // The nightly arrears step: days past due and IN_ARREARS from the oldest unsettled instalment.
        tx.executeWithoutResult(s -> {
            Loan loan = loanRepository.findById(loanId).orElseThrow();
            LoanAccrualService.updateArrears(loan, today);
            loanRepository.save(loan);
        });
        assertThat(jdbc.queryForObject("SELECT status FROM loans WHERE id = ?", String.class, loanId))
                .isEqualTo(LoanStatus.IN_ARREARS.name());
        assertThat(jdbc.queryForObject("SELECT days_past_due FROM loans WHERE id = ?", Integer.class, loanId))
                .isEqualTo(70);

        BigDecimal expectedOverdue = totalDue(loanId, 1).add(totalDue(loanId, 2)).subtract(BigDecimal.TEN);

        // Instalment-level figures: 1 and 2 are overdue, 3 is paid, the rest aren't due.
        assertThat(scheduleRepository.countOverdue(today) - countBefore).isEqualTo(2);
        assertThat(scheduleRepository.sumOverdueBalance(today).subtract(balanceBefore))
                .isEqualByComparingTo(expectedOverdue);

        // One loan, one bucket: aged by its oldest unsettled instalment (61–90 days).
        long[] bucketsAfter = buckets(today);
        assertThat(new long[]{
                bucketsAfter[0] - bucketsBefore[0], bucketsAfter[1] - bucketsBefore[1],
                bucketsAfter[2] - bucketsBefore[2], bucketsAfter[3] - bucketsBefore[3]})
                .containsExactly(0, 0, 1, 0);

        // The dashboard endpoint reports the same instalment figures.
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("it", null, "ROLE_ADMIN"));
        DashboardController.RepaymentAnalyticsResponse analytics = dashboard.getRepaymentAnalytics().getBody().data();
        assertThat(analytics.overdueInstallmentCount() - countBefore).isEqualTo(2);

        // The LoansInArrears report lists the loan with its oldest overdue date and amount.
        String accountNumber = jdbc.queryForObject("SELECT loan_account_number FROM loans WHERE id = ?", String.class, loanId);
        List<Map<String, Object>> rows = reportService.runReport("LoansInArrears", Map.of());
        Map<String, Object> row = rows.stream()
                .filter(r -> accountNumber.equals(r.get("loan_account_number"))).findFirst().orElseThrow();
        assertThat(((Number) row.get("days_overdue")).intValue()).isEqualTo(70);
        assertThat((BigDecimal) row.get("amount_overdue")).isEqualByComparingTo(expectedOverdue);
        assertThat(row.get("ageing_bucket")).isEqualTo("61-90 days");

        // Paying the rest of instalments 1 and 2 clears them from every figure.
        jdbc.update("""
                UPDATE loan_repayment_schedule SET principal_paid = principal_due, interest_paid = interest_due,
                       total_paid = total_due, status = 'PAID'
                WHERE loan_id = ? AND installment_no IN (1, 2)""", loanId);
        assertThat(scheduleRepository.countOverdue(today)).isEqualTo(countBefore);
        assertThat(reportService.runReport("LoansInArrears", Map.of()))
                .noneMatch(r -> accountNumber.equals(r.get("loan_account_number")));
    }

    @Test
    @DisplayName("every seeded report loads: V9's categories ('Loans', 'Savings', …) now match the enum")
    void seededReportsLoad() {
        assertThat(reportRepository.findAll())
                .extracting(Report::getReportName)
                .contains("ActiveLoans", "LoansInArrears", "SavingsBalance", "TellerCashPosition",
                        "CustomerAcquisition", "TrialBalance", "LoanProductSummary");
        assertThat(reportRepository.findByReportName("LoanProductSummary").orElseThrow().getCategory())
                .isEqualTo(Report.Category.LOAN);
    }

    /** [1–30, 31–60, 61–90, 90+] days past due, as the dashboard buckets loans in arrears. */
    private long[] buckets(LocalDate today) {
        return new long[]{
                loanRepository.countInArrearsOverdueSinceBetween(today.minusDays(30), today),
                loanRepository.countInArrearsOverdueSinceBetween(today.minusDays(60), today.minusDays(30)),
                loanRepository.countInArrearsOverdueSinceBetween(today.minusDays(90), today.minusDays(60)),
                loanRepository.countInArrearsOverdueSinceBefore(today.minusDays(90))};
    }

    private UUID disbursedLoan() {
        UUID account = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', 5000.00, 'USD', DATE '2025-01-01', 'loan-overdue-it')""",
                account, "LN-OVD-" + account.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT);
        UUID loanId = loanService.applyForLoan(new LoanApplicationRequest(
                DEMO_CUSTOMER, USD_LOAN_PRODUCT, account, new BigDecimal("1200.00"), 12, "overdue-it")).id();
        loanService.approveLoan(loanId, "it");
        loanService.disburseLoan(loanId);
        return loanId;
    }

    private void moveDue(UUID loanId, int installment, LocalDate dueDate) {
        jdbc.update("UPDATE loan_repayment_schedule SET due_date = ? WHERE loan_id = ? AND installment_no = ?",
                dueDate, loanId, installment);
    }

    private BigDecimal totalDue(UUID loanId, int installment) {
        return jdbc.queryForObject("SELECT total_due FROM loan_repayment_schedule WHERE loan_id = ? AND installment_no = ?",
                BigDecimal.class, loanId, installment);
    }
}

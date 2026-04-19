package com.cba.common;

import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import com.cba.account.AccountType;
import com.cba.account.TransactionRepository;
import com.cba.common.response.ApiResponse;
import com.cba.customer.CustomerRepository;
import com.cba.customer.KycStatus;
import com.cba.loan.LoanRepaymentScheduleRepository;
import com.cba.loan.LoanRepository;
import com.cba.loan.LoanStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Real-time KPI and analytics endpoints")
public class DashboardController {

    private final CustomerRepository               customerRepo;
    private final LoanRepository                   loanRepo;
    private final AccountRepository                accountRepo;
    private final TransactionRepository            transactionRepo;
    private final LoanRepaymentScheduleRepository  scheduleRepo;

    public DashboardController(CustomerRepository customerRepo,
                               LoanRepository loanRepo,
                               AccountRepository accountRepo,
                               TransactionRepository transactionRepo,
                               LoanRepaymentScheduleRepository scheduleRepo) {
        this.customerRepo    = customerRepo;
        this.loanRepo        = loanRepo;
        this.accountRepo     = accountRepo;
        this.transactionRepo = transactionRepo;
        this.scheduleRepo    = scheduleRepo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Dashboard KPIs — all counters and totals in a single call")
    public ResponseEntity<ApiResponse<DashboardKpiResponse>> getKpis() {

        LocalDate today = LocalDate.now();

        long totalCustomers    = customerRepo.count();
        long kycPending        = customerRepo.countByKycStatus(KycStatus.PENDING_KYC);
        long activeLoans       = loanRepo.countByStatus(LoanStatus.ACTIVE)
                               + loanRepo.countByStatus(LoanStatus.IN_ARREARS)
                               + loanRepo.countByStatus(LoanStatus.DISBURSED);
        long loansInArrears    = loanRepo.countByStatus(LoanStatus.IN_ARREARS);
        long totalDeposits     = accountRepo.countByStatus(AccountStatus.ACTIVE);
        BigDecimal depositBalance = accountRepo.sumAllActiveBalances();
        long todayTransactions = transactionRepo.countByValueDate(today);

        var kpis = new DashboardKpiResponse(
                totalCustomers,
                kycPending,
                activeLoans,
                loansInArrears,
                totalDeposits,
                depositBalance != null ? depositBalance : BigDecimal.ZERO,
                todayTransactions
        );

        return ResponseEntity.ok(ApiResponse.ok(kpis));
    }

    @GetMapping("/analytics/loans")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Loan portfolio aging breakdown by overdue bucket")
    public ResponseEntity<ApiResponse<LoanPortfolioResponse>> getLoanPortfolio() {

        LocalDate today = LocalDate.now();

        // Active loans with no overdue installments = "current"
        long totalActive = loanRepo.countByStatus(LoanStatus.ACTIVE)
                         + loanRepo.countByStatus(LoanStatus.DISBURSED);
        long inArrears   = loanRepo.countByStatus(LoanStatus.IN_ARREARS);
        long writtenOff  = loanRepo.countByStatus(LoanStatus.WRITTEN_OFF);
        long foreclosed  = loanRepo.countByStatus(LoanStatus.FORECLOSED);

        // Bucket IN_ARREARS loans by age of oldest OVERDUE installment
        LocalDate d30  = today.minusDays(30);
        LocalDate d60  = today.minusDays(60);
        LocalDate d90  = today.minusDays(90);

        // 0–30 days past due (OVERDUE installment within last 30 days)
        long bucket30  = loanRepo.countLoansWithOverdueBetween(d30, today);
        // 31–60 days past due
        long bucket60  = loanRepo.countLoansWithOverdueBetween(d60, d30);
        // 61–90 days past due
        long bucket90  = loanRepo.countLoansWithOverdueBetween(d90, d60);
        // 90+ days past due
        long bucket90p = loanRepo.countLoansWithOverdueBefore(d90);

        long total = totalActive + inArrears + writtenOff + foreclosed;
        if (total == 0) total = 1; // avoid division by zero

        double pctCurrent = round((double)(totalActive + bucket30) / total * 100);
        double pct30to60  = round((double) bucket60  / total * 100);
        double pct60to90  = round((double) bucket90  / total * 100);
        double pct90plus  = round((double)(bucket90p + writtenOff + foreclosed) / total * 100);

        var portfolio = new LoanPortfolioResponse(
                totalActive + inArrears + writtenOff + foreclosed,
                pctCurrent, pct30to60, pct60to90, pct90plus,
                totalActive, inArrears, writtenOff + foreclosed
        );

        return ResponseEntity.ok(ApiResponse.ok(portfolio));
    }

    @GetMapping("/analytics/deposits")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Deposit portfolio breakdown by account type with balance totals")
    public ResponseEntity<ApiResponse<DepositAnalyticsResponse>> getDepositAnalytics() {

        LocalDate today     = LocalDate.now();
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());

        List<Object[]> rows = accountRepo.countAndSumByType();

        long savingsCount = 0, checkingCount = 0, fixedCount = 0;
        BigDecimal savingsBalance = BigDecimal.ZERO,
                   checkingBalance = BigDecimal.ZERO,
                   fixedBalance = BigDecimal.ZERO;

        for (Object[] row : rows) {
            AccountType type    = (AccountType) row[0];
            long        count   = (Long) row[1];
            BigDecimal  balance = (BigDecimal) row[2];
            switch (type) {
                case SAVINGS       -> { savingsCount = count;  savingsBalance = balance; }
                case CHECKING      -> { checkingCount = count; checkingBalance = balance; }
                case FIXED_DEPOSIT -> { fixedCount = count;    fixedBalance = balance; }
            }
        }

        long       newThisMonth  = accountRepo.countOpenedBetween(monthStart, today);
        BigDecimal avgBalance    = accountRepo.avgActiveBalance();

        var response = new DepositAnalyticsResponse(
                savingsCount, savingsBalance,
                checkingCount, checkingBalance,
                fixedCount, fixedBalance,
                newThisMonth,
                avgBalance != null ? avgBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/analytics/repayments")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Repayment collection performance metrics for the current month")
    public ResponseEntity<ApiResponse<RepaymentAnalyticsResponse>> getRepaymentAnalytics() {

        LocalDate today      = LocalDate.now();
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate monthEnd   = today.with(TemporalAdjusters.lastDayOfMonth());

        long       totalDue       = scheduleRepo.countDueBetween(monthStart, monthEnd);
        long       totalPaid      = scheduleRepo.countPaidBetween(monthStart, monthEnd);
        BigDecimal amountDue      = scheduleRepo.sumDueBetween(monthStart, monthEnd);
        BigDecimal amountCollected= scheduleRepo.sumCollectedBetween(monthStart, monthEnd);
        long       overdueCount   = scheduleRepo.countOverdue();
        BigDecimal overdueBalance = scheduleRepo.sumOverdueBalance();

        double collectionRate = (amountDue.compareTo(BigDecimal.ZERO) == 0)
                ? 0.0
                : amountCollected.multiply(BigDecimal.valueOf(100))
                                 .divide(amountDue, 1, RoundingMode.HALF_UP)
                                 .doubleValue();

        var response = new RepaymentAnalyticsResponse(
                totalDue, totalPaid,
                amountDue.setScale(2, RoundingMode.HALF_UP),
                amountCollected.setScale(2, RoundingMode.HALF_UP),
                collectionRate,
                overdueCount,
                overdueBalance.setScale(2, RoundingMode.HALF_UP)
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public record DashboardKpiResponse(
            long totalCustomers,
            long kycPending,
            long activeLoans,
            long loansInArrears,
            long totalDeposits,
            BigDecimal depositBalance,
            long todayTransactions
    ) {}

    public record LoanPortfolioResponse(
            long totalLoans,
            double pctCurrent,
            double pct30to60,
            double pct60to90,
            double pct90plus,
            long countActive,
            long countInArrears,
            long countWrittenOff
    ) {}

    public record DepositAnalyticsResponse(
            long savingsCount,
            BigDecimal savingsBalance,
            long checkingCount,
            BigDecimal checkingBalance,
            long fixedDepositCount,
            BigDecimal fixedDepositBalance,
            long newThisMonth,
            BigDecimal averageBalance
    ) {}

    public record RepaymentAnalyticsResponse(
            long installmentsDueThisMonth,
            long installmentsPaidThisMonth,
            BigDecimal amountDueThisMonth,
            BigDecimal amountCollectedThisMonth,
            double collectionRate,
            long overdueInstallmentCount,
            BigDecimal overdueBalance
    ) {}
}

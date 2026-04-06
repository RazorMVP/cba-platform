package com.cba.loan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RepaymentScheduleEngine — annuity EMI calculation")
class RepaymentScheduleEngineTest {

    private RepaymentScheduleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RepaymentScheduleEngine();
    }

    @Test
    @DisplayName("EMI is correct for a standard personal loan: 10,000 at 14.99% for 24 months")
    void calculateEmi_standardLoan() {
        BigDecimal principal = new BigDecimal("10000.00");
        BigDecimal annualRate = new BigDecimal("14.99");
        int months = 24;

        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 20, RoundingMode.HALF_UP);
        BigDecimal emi = engine.calculateEmi(principal, monthlyRate, months);

        // Expected ~484.66 — verified against standard amortization tables
        assertThat(emi).isBetween(new BigDecimal("484.00"), new BigDecimal("486.00"));
    }

    @Test
    @DisplayName("Schedule has exactly termMonths installments")
    void generateSchedule_correctInstallmentCount() {
        Loan loan = new Loan();
        List<LoanRepaymentSchedule> schedule = engine.generateAnnuitySchedule(
            loan,
            new BigDecimal("10000.00"),
            new BigDecimal("14.99"),
            24,
            LocalDate.now().plusMonths(1)
        );

        assertThat(schedule).hasSize(24);
    }

    @Test
    @DisplayName("Sum of all principal_due equals the original loan principal")
    void generateSchedule_principalSumsToOriginal() {
        Loan loan = new Loan();
        BigDecimal principal = new BigDecimal("10000.00");

        List<LoanRepaymentSchedule> schedule = engine.generateAnnuitySchedule(
            loan, principal, new BigDecimal("14.99"), 24,
            LocalDate.now().plusMonths(1)
        );

        BigDecimal sumPrincipal = schedule.stream()
            .map(LoanRepaymentSchedule::getPrincipalDue)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        assertThat(sumPrincipal).isEqualByComparingTo(principal);
    }

    @Test
    @DisplayName("First due date is one month from today, subsequent dates increment by month")
    void generateSchedule_dueDatesAreSequential() {
        Loan loan = new Loan();
        LocalDate firstDue = LocalDate.of(2024, 6, 5);

        List<LoanRepaymentSchedule> schedule = engine.generateAnnuitySchedule(
            loan, new BigDecimal("5000.00"), new BigDecimal("10.00"), 6, firstDue
        );

        for (int i = 0; i < schedule.size(); i++) {
            assertThat(schedule.get(i).getDueDate()).isEqualTo(firstDue.plusMonths(i));
            assertThat(schedule.get(i).getInstallmentNo()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("Zero-interest loan splits principal equally across all installments")
    void calculateEmi_zeroInterest() {
        BigDecimal principal = new BigDecimal("6000.00");
        BigDecimal emi = engine.calculateEmi(principal, BigDecimal.ZERO, 6);

        assertThat(emi).isEqualByComparingTo(new BigDecimal("1000.0000"));
    }

    @Test
    @DisplayName("All installments have non-negative interest and principal")
    void generateSchedule_allAmountsPositive() {
        Loan loan = new Loan();
        engine.generateAnnuitySchedule(
            loan, new BigDecimal("25000.00"), new BigDecimal("8.50"), 36,
            LocalDate.now().plusMonths(1)
        ).forEach(s -> {
            assertThat(s.getPrincipalDue()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(s.getInterestDue()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(s.getTotalDue()).isGreaterThan(BigDecimal.ZERO);
        });
    }
}

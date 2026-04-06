package com.cba.loan;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates amortization schedules for loan products.
 * Supports ANNUITY (equal installments / EMI) — the CBA default.
 *
 * EMI formula: P × r × (1+r)^n / ((1+r)^n − 1)
 * where r = monthly rate = annualRate / 1200 (rate stored as percentage, e.g. 14.99)
 */
@Component
public class RepaymentScheduleEngine {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final int SCALE = 4;

    public List<LoanRepaymentSchedule> generateAnnuitySchedule(
            Loan loan, BigDecimal principal, BigDecimal annualRatePercent,
            int termMonths, LocalDate firstDueDate) {

        // Monthly rate: annualRate% / 1200
        BigDecimal r = annualRatePercent.divide(BigDecimal.valueOf(1200), MC);

        BigDecimal emi = calculateEmi(principal, r, termMonths);

        List<LoanRepaymentSchedule> schedule = new ArrayList<>(termMonths);
        BigDecimal outstandingPrincipal = principal;

        for (int i = 1; i <= termMonths; i++) {
            BigDecimal interestDue = outstandingPrincipal.multiply(r, MC)
                .setScale(SCALE, RoundingMode.HALF_UP);

            BigDecimal principalDue;
            if (i == termMonths) {
                // Last installment: remaining principal to avoid rounding residuals
                principalDue = outstandingPrincipal;
            } else {
                principalDue = emi.subtract(interestDue).setScale(SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal totalDue = principalDue.add(interestDue).setScale(SCALE, RoundingMode.HALF_UP);

            LoanRepaymentSchedule installment = new LoanRepaymentSchedule();
            installment.setLoan(loan);
            installment.setInstallmentNo(i);
            installment.setDueDate(firstDueDate.plusMonths(i - 1L));
            installment.setPrincipalDue(principalDue);
            installment.setInterestDue(interestDue);
            installment.setFeesDue(BigDecimal.ZERO);
            installment.setTotalDue(totalDue);

            schedule.add(installment);
            outstandingPrincipal = outstandingPrincipal.subtract(principalDue);
        }

        return schedule;
    }

    /**
     * EMI = P × r × (1+r)^n / ((1+r)^n − 1)
     */
    BigDecimal calculateEmi(BigDecimal principal, BigDecimal monthlyRate, int termMonths) {
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            // Zero-interest loan: equal principal splits
            return principal.divide(BigDecimal.valueOf(termMonths), SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate, MC);
        BigDecimal onePlusRpowN = onePlusR.pow(termMonths, MC);
        BigDecimal numerator = principal.multiply(monthlyRate, MC).multiply(onePlusRpowN, MC);
        BigDecimal denominator = onePlusRpowN.subtract(BigDecimal.ONE, MC);
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }
}

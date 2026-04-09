package com.cba.product.dto;

import com.cba.product.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LoanProductRequest(

        // ── Core identifiers ─────────────────────────────────────────
        @NotBlank @Size(max = 100)
        String name,

        @NotBlank @Size(max = 4)
        String shortName,

        String description,

        @Size(min = 3, max = 3)
        String currencyCode,

        UUID fundId,

        // ── Principal range ──────────────────────────────────────────
        @NotNull @DecimalMin("1.00")
        BigDecimal minPrincipal,

        @NotNull @DecimalMin("1.00")
        BigDecimal maxPrincipal,

        BigDecimal defaultPrincipal,

        Integer installmentAmountInMultiplesOf,

        // ── Interest rates ───────────────────────────────────────────
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal minInterestRate,

        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal maxInterestRate,

        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal defaultInterestRate,

        InterestRateFrequencyType interestRateFrequencyType,

        InterestType interestType,

        AmortizationType amortizationType,

        InterestCalculationPeriodType interestCalculationPeriodType,

        DaysInYearType daysInYearType,

        DaysInMonthType daysInMonthType,

        // ── Repayment schedule ───────────────────────────────────────
        @NotNull @Min(1)
        Integer minTermMonths,

        @NotNull @Min(1)
        Integer maxTermMonths,

        @Min(1)
        Integer numberOfRepayments,

        @Min(1)
        Integer repaymentEvery,

        RepaymentFrequencyType repaymentFrequencyType,

        RepaymentType repaymentType,

        // ── Grace periods ────────────────────────────────────────────
        @Min(0) Integer graceOnPrincipalPayment,
        @Min(0) Integer graceOnInterestPayment,
        @Min(0) Integer graceOnInterestCharged,
        @Min(0) Integer graceOnArrearsAgeing,

        @DecimalMin("0.00")
        BigDecimal inArrearsTolerance,

        // ── Fees ─────────────────────────────────────────────────────
        BigDecimal originationFee,
        BigDecimal latePaymentFee,

        // ── Attribute overrides ──────────────────────────────────────
        AllowAttributeOverridesRequest allowAttributeOverrides,

        // ── GL account linkages ──────────────────────────────────────
        UUID fundSourceAccountId,
        UUID loanPortfolioAccountId,
        UUID transfersInSuspenseAccountId,
        UUID interestOnLoanAccountId,
        UUID incomeFromFeesAccountId,
        UUID incomeFromPenaltiesAccountId,
        UUID writeOffAccountId,
        UUID overpaymentLiabilityAccountId,

        // ── Charges ──────────────────────────────────────────────────
        List<UUID> chargeIds
) {
    public record AllowAttributeOverridesRequest(
            Boolean amortizationType,
            Boolean interestType,
            Boolean repaymentEvery,
            Boolean repaymentFrequency,
            Boolean repaymentStrategy,
            Boolean graceOnPrincipalAndInterestPayment,
            Boolean graceOnInterestCharged,
            Boolean interestRatePerPeriod
    ) {}
}

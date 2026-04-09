package com.cba.product.dto;

import com.cba.product.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DepositProductRequest(

        // ── Core identifiers ─────────────────────────────────────────
        @NotBlank @Size(max = 100)
        String name,

        @NotBlank @Size(max = 4)
        String shortName,

        String description,

        @NotNull
        DepositAccountType accountType,

        @Size(min = 3, max = 3)
        String currencyCode,

        // ── Balance constraints ──────────────────────────────────────
        @DecimalMin("0.00")
        BigDecimal minimumBalance,

        @DecimalMin("0.00")
        BigDecimal minRequiredOpeningBalance,

        // ── Interest configuration ───────────────────────────────────
        @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal interestRate,

        InterestCompounding interestCompounding,

        InterestPostingPeriodType interestPostingPeriodType,

        DaysInYearType daysInYearType,

        DaysInMonthType daysInMonthType,

        // ── Lock-in period ───────────────────────────────────────────
        @Min(0)
        Integer lockinPeriodFrequency,

        LockInFrequencyType lockinPeriodFrequencyType,

        // ── Withdrawal settings ──────────────────────────────────────
        Boolean withdrawalFeeForTransfers,

        // ── Overdraft ────────────────────────────────────────────────
        Boolean allowOverdraft,

        @DecimalMin("0.00")
        BigDecimal overdraftLimit,

        @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal nominalAnnualInterestRateOverdraft,

        @DecimalMin("0.00")
        BigDecimal minOverdraftForInterestCalculation,

        // ── Accounting ───────────────────────────────────────────────
        AccountingType accountingType,

        // ── GL account linkages ──────────────────────────────────────
        UUID savingsReferenceAccountId,
        UUID savingsControlAccountId,
        UUID transfersInSuspenseAccountId,
        UUID interestOnSavingsAccountId,
        UUID incomeFromFeesAccountId,
        UUID incomeFromPenaltiesAccountId,
        UUID writeOffAccountId,
        UUID overdraftPortfolioControlAccountId,

        // ── Charges ──────────────────────────────────────────────────
        List<UUID> chargeIds
) {}

package com.cba.product.dto;

import com.cba.accounting.GlAccount;
import com.cba.charge.ChargeDefinition;
import com.cba.product.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DepositProductResponse(
        UUID id,
        String name,
        String shortName,
        String description,
        DepositAccountType accountType,
        String currencyCode,

        // Balance
        BigDecimal minimumBalance,
        BigDecimal minRequiredOpeningBalance,

        // Interest
        BigDecimal interestRate,
        InterestCompounding interestCompounding,
        InterestPostingPeriodType interestPostingPeriodType,
        DaysInYearType daysInYearType,
        DaysInMonthType daysInMonthType,

        // Lock-in
        Integer lockinPeriodFrequency,
        LockInFrequencyType lockinPeriodFrequencyType,

        // Withdrawal
        boolean withdrawalFeeForTransfers,

        // Overdraft
        boolean allowOverdraft,
        BigDecimal overdraftLimit,
        BigDecimal nominalAnnualInterestRateOverdraft,
        BigDecimal minOverdraftForInterestCalculation,

        // Accounting
        AccountingType accountingType,
        GlAccountRef savingsReferenceAccount,
        GlAccountRef savingsControlAccount,
        GlAccountRef transfersInSuspenseAccount,
        GlAccountRef interestOnSavingsAccount,
        GlAccountRef incomeFromFeesAccount,
        GlAccountRef incomeFromPenaltiesAccount,
        GlAccountRef writeOffAccount,
        GlAccountRef overdraftPortfolioControlAccount,

        // Charges
        List<ChargeRef> charges,

        boolean active
) {
    public record GlAccountRef(UUID id, String glCode, String name) {
        public static GlAccountRef of(GlAccount a) {
            return a == null ? null : new GlAccountRef(a.getId(), a.getGlCode(), a.getName());
        }
    }

    public record ChargeRef(UUID id, String name, String chargeTimeType) {
        public static ChargeRef of(ChargeDefinition c) {
            return new ChargeRef(c.getId(), c.getName(), c.getChargeTimeType().name());
        }
    }

    public static DepositProductResponse from(DepositProduct p) {
        return new DepositProductResponse(
                p.getId(), p.getName(), p.getShortName(), p.getDescription(),
                p.getAccountType(), p.getCurrencyCode(),
                p.getMinimumBalance(), p.getMinRequiredOpeningBalance(),
                p.getInterestRate(), p.getInterestCompounding(),
                p.getInterestPostingPeriodType(), p.getDaysInYearType(), p.getDaysInMonthType(),
                p.getLockinPeriodFrequency(), p.getLockinPeriodFrequencyType(),
                p.isWithdrawalFeeForTransfers(),
                p.isAllowOverdraft(), p.getOverdraftLimit(),
                p.getNominalAnnualInterestRateOverdraft(), p.getMinOverdraftForInterestCalculation(),
                p.getAccountingType(),
                GlAccountRef.of(p.getSavingsReferenceAccount()),
                GlAccountRef.of(p.getSavingsControlAccount()),
                GlAccountRef.of(p.getTransfersInSuspenseAccount()),
                GlAccountRef.of(p.getInterestOnSavingsAccount()),
                GlAccountRef.of(p.getIncomeFromFeesAccount()),
                GlAccountRef.of(p.getIncomeFromPenaltiesAccount()),
                GlAccountRef.of(p.getWriteOffAccount()),
                GlAccountRef.of(p.getOverdraftPortfolioControlAccount()),
                p.getCharges().stream().map(ChargeRef::of).toList(),
                p.isActive()
        );
    }
}

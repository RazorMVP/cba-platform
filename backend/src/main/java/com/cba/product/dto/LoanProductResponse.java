package com.cba.product.dto;

import com.cba.accounting.GlAccount;
import com.cba.charge.ChargeDefinition;
import com.cba.product.*;
import com.cba.system.Fund;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LoanProductResponse(

        UUID id,
        String name,
        String shortName,
        String description,
        String currencyCode,
        FundRef fund,

        // Principal
        BigDecimal minPrincipal,
        BigDecimal maxPrincipal,
        BigDecimal defaultPrincipal,
        Integer installmentAmountInMultiplesOf,

        // Interest rates
        BigDecimal minInterestRate,
        BigDecimal maxInterestRate,
        BigDecimal defaultInterestRate,
        InterestRateFrequencyType interestRateFrequencyType,
        InterestType interestType,
        AmortizationType amortizationType,
        InterestCalculationPeriodType interestCalculationPeriodType,
        DaysInYearType daysInYearType,
        DaysInMonthType daysInMonthType,

        // Repayment schedule
        int minTermMonths,
        int maxTermMonths,
        int numberOfRepayments,
        int repaymentEvery,
        RepaymentFrequencyType repaymentFrequencyType,
        RepaymentType repaymentType,

        // Grace periods
        Integer graceOnPrincipalPayment,
        Integer graceOnInterestPayment,
        Integer graceOnInterestCharged,
        Integer graceOnArrearsAgeing,
        BigDecimal inArrearsTolerance,

        // Fees
        BigDecimal originationFee,
        BigDecimal latePaymentFee,

        // Attribute overrides
        AllowAttributeOverridesResponse allowAttributeOverrides,

        // Accounting
        GlAccountRef fundSourceAccount,
        GlAccountRef loanPortfolioAccount,
        GlAccountRef transfersInSuspenseAccount,
        GlAccountRef interestOnLoanAccount,
        GlAccountRef incomeFromFeesAccount,
        GlAccountRef incomeFromPenaltiesAccount,
        GlAccountRef writeOffAccount,
        GlAccountRef overpaymentLiabilityAccount,

        // Charges
        List<ChargeRef> charges,

        boolean active
) {
    // ── Nested refs ──────────────────────────────────────────────────

    public record GlAccountRef(UUID id, String glCode, String name) {
        public static GlAccountRef of(GlAccount a) {
            return a == null ? null : new GlAccountRef(a.getId(), a.getGlCode(), a.getName());
        }
    }

    public record FundRef(UUID id, String name) {
        public static FundRef of(Fund f) {
            return f == null ? null : new FundRef(f.getId(), f.getName());
        }
    }

    public record ChargeRef(UUID id, String name, String chargeTimeType) {
        public static ChargeRef of(ChargeDefinition c) {
            return new ChargeRef(c.getId(), c.getName(), c.getChargeTimeType().name());
        }
    }

    public record AllowAttributeOverridesResponse(
            boolean amortizationType,
            boolean interestType,
            boolean repaymentEvery,
            boolean repaymentFrequency,
            boolean repaymentStrategy,
            boolean graceOnPrincipalAndInterestPayment,
            boolean graceOnInterestCharged,
            boolean interestRatePerPeriod
    ) {
        public static AllowAttributeOverridesResponse of(AllowAttributeOverrides o) {
            if (o == null) return null;
            return new AllowAttributeOverridesResponse(
                    o.isAmortizationType(), o.isInterestType(), o.isRepaymentEvery(),
                    o.isRepaymentFrequency(), o.isRepaymentStrategy(),
                    o.isGraceOnPrincipalAndInterestPayment(),
                    o.isGraceOnInterestCharged(), o.isInterestRatePerPeriod()
            );
        }
    }

    // ── Factory ──────────────────────────────────────────────────────

    public static LoanProductResponse from(LoanProduct p) {
        return new LoanProductResponse(
                p.getId(), p.getName(), p.getShortName(), p.getDescription(), p.getCurrencyCode(),
                FundRef.of(p.getFund()),
                p.getMinPrincipal(), p.getMaxPrincipal(), p.getDefaultPrincipal(),
                p.getInstallmentAmountInMultiplesOf(),
                p.getMinInterestRate(), p.getMaxInterestRate(), p.getDefaultInterestRate(),
                p.getInterestRateFrequencyType(), p.getInterestType(), p.getAmortizationType(),
                p.getInterestCalculationPeriodType(), p.getDaysInYearType(), p.getDaysInMonthType(),
                p.getMinTermMonths(), p.getMaxTermMonths(),
                p.getNumberOfRepayments(), p.getRepaymentEvery(), p.getRepaymentFrequencyType(),
                p.getRepaymentType(),
                p.getGraceOnPrincipalPayment(), p.getGraceOnInterestPayment(),
                p.getGraceOnInterestCharged(), p.getGraceOnArrearsAgeing(), p.getInArrearsTolerance(),
                p.getOriginationFee(), p.getLatePaymentFee(),
                AllowAttributeOverridesResponse.of(p.getAllowAttributeOverrides()),
                GlAccountRef.of(p.getFundSourceAccount()),
                GlAccountRef.of(p.getLoanPortfolioAccount()),
                GlAccountRef.of(p.getTransfersInSuspenseAccount()),
                GlAccountRef.of(p.getInterestOnLoanAccount()),
                GlAccountRef.of(p.getIncomeFromFeesAccount()),
                GlAccountRef.of(p.getIncomeFromPenaltiesAccount()),
                GlAccountRef.of(p.getWriteOffAccount()),
                GlAccountRef.of(p.getOverpaymentLiabilityAccount()),
                p.getCharges().stream().map(ChargeRef::of).toList(),
                p.isActive()
        );
    }
}

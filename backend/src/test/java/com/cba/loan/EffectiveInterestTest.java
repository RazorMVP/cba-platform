package com.cba.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EffectiveInterest — origination fee spread by the effective interest method")
class EffectiveInterestTest {

    static final LocalDate START = LocalDate.of(2026, 1, 1);

    /** A 12-month 24% annuity on 12,000, from the real schedule engine. */
    private List<LoanRepaymentSchedule> schedule() {
        return new RepaymentScheduleEngine().generateAnnuitySchedule(
                new Loan(), new BigDecimal("12000.00"), new BigDecimal("24.00"), 12, START.plusMonths(1));
    }

    @Test
    @DisplayName("no fee: nothing to spread")
    void noFee_allZero() {
        assertThat(EffectiveInterest.feeAmortisation(new BigDecimal("12000.00"), BigDecimal.ZERO, START, schedule()))
                .hasSize(12).allSatisfy(s -> assertThat(s).isEqualByComparingTo("0"));
    }

    @Test
    @DisplayName("the shares sum exactly to the fee, are front-loaded and never negative")
    void fee_sumsExactlyAndFrontLoaded() {
        List<BigDecimal> shares = EffectiveInterest.feeAmortisation(
                new BigDecimal("12000.00"), new BigDecimal("240.00"), START, schedule());

        assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("240.00");
        assertThat(shares).allSatisfy(s -> assertThat(s.signum()).isGreaterThanOrEqualTo(0));
        // The carrying amount falls as the loan amortises, so the effective-interest share does too.
        assertThat(shares.get(0)).isGreaterThan(shares.get(10));
    }

    @Test
    @DisplayName("the effective rate is above the contractual rate when a fee is deducted")
    void dailyRate_aboveContractual() {
        double withFee = EffectiveInterest.dailyRate(11760, START, schedule());
        double withoutFee = EffectiveInterest.dailyRate(12000, START, schedule());

        assertThat(withFee).isGreaterThan(withoutFee);
        // 24% a year compounded monthly ≈ 0.0651% a day; within a few hundredths of a basis point.
        assertThat(withoutFee).isBetween(0.00063, 0.00067);
    }
}

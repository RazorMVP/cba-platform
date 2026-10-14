package com.cba.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    // ── Modification (IFRS 9 §5.4.3) ─────────────────────────────────────────

    /** 1,000 at 24%, two monthly instalments from Jan 1 (31 and 59 days): 20.0000 and 10.0990 interest. */
    private Loan twoInstalmentLoan() {
        Loan loan = new Loan();
        loan.setApprovedAmount(new BigDecimal("1000.00"));
        loan.setDisbursementDate(START);
        loan.setRepaymentSchedule(new java.util.ArrayList<>(new RepaymentScheduleEngine().generateAnnuitySchedule(
                loan, new BigDecimal("1000.00"), new BigDecimal("24.00"), 2, START.plusMonths(1))));
        return loan;
    }

    @Test
    @DisplayName("waiving all future interest at disbursement: loss = its PV, unwound exactly over the two periods")
    void modification_allInterestAtDayZero() {
        Loan loan = twoInstalmentLoan();
        double rate = EffectiveInterest.originalDailyRate(loan);
        List<EffectiveInterest.CashFlow> interest = loan.getRepaymentSchedule().stream()
                .map(i -> new EffectiveInterest.CashFlow(i.getDueDate(), i.getInterestDue())).toList();
        BigDecimal loss = EffectiveInterest.presentValue(interest, START, rate);
        assertThat(loss).isCloseTo(new BigDecimal("29.3083"), within(new BigDecimal("0.005")));

        for (LoanRepaymentSchedule i : loan.getRepaymentSchedule()) {
            i.setInterestCancelled(i.getInterestDue());
            i.setInterestDue(BigDecimal.ZERO);
        }
        // The rate is the original one even though the schedule changed.
        assertThat(EffectiveInterest.originalDailyRate(loan)).isEqualTo(rate);

        EffectiveInterest.spreadModificationDiscount(loan, START, rate, loss);
        BigDecimal first = loan.getRepaymentSchedule().get(0).getModificationUnwind();
        BigDecimal second = loan.getRepaymentSchedule().get(1).getModificationUnwind();
        // Each period's unwind is the effective-rate revenue on the modified carrying amount.
        assertThat(first).isCloseTo(new BigDecimal("20.0633"), within(new BigDecimal("0.005")));
        assertThat(second).isCloseTo(new BigDecimal("9.2450"), within(new BigDecimal("0.005")));
        assertThat(first.add(second)).isEqualByComparingTo(loss);
    }

    @Test
    @DisplayName("the stored rate wins; without one it comes from the contractual schedule")
    void originalDailyRate_storedOrComputed() {
        Loan loan = twoInstalmentLoan();
        double computed = EffectiveInterest.originalDailyRate(loan);
        assertThat(computed).isEqualTo(EffectiveInterest.dailyRate(1000, START, loan.getRepaymentSchedule()));
        loan.setEffectiveDailyRate(0.0007);
        assertThat(EffectiveInterest.originalDailyRate(loan)).isEqualTo(0.0007);
    }

    @Test
    @DisplayName("a discount spread from mid-period never goes negative and still adds up exactly")
    void modification_midPeriod() {
        Loan loan = twoInstalmentLoan();
        double rate = EffectiveInterest.originalDailyRate(loan);
        LocalDate day10 = START.plusDays(10);
        LoanRepaymentSchedule first = loan.getRepaymentSchedule().get(0);
        LoanRepaymentSchedule second = loan.getRepaymentSchedule().get(1);
        // 10 of 31 days of the first period accrued, then derecognised; the rest of the interest given up.
        first.setInterestAccrued(new BigDecimal("6.4516"));
        first.setInterestWaived(new BigDecimal("6.4516"));
        BigDecimal loss = EffectiveInterest.presentValue(List.of(
                new EffectiveInterest.CashFlow(first.getDueDate(), first.getInterestDue().subtract(new BigDecimal("6.4516"))),
                new EffectiveInterest.CashFlow(second.getDueDate(), second.getInterestDue())), day10, rate);
        assertThat(loss).isCloseTo(new BigDecimal("23.1396"), within(new BigDecimal("0.005")));
        first.setInterestCancelled(first.getInterestDue().subtract(new BigDecimal("6.4516")));
        first.setInterestDue(new BigDecimal("6.4516"));
        second.setInterestCancelled(second.getInterestDue());
        second.setInterestDue(BigDecimal.ZERO);

        EffectiveInterest.spreadModificationDiscount(loan, day10, rate, loss);

        assertThat(first.getModificationUnwind().signum()).isPositive();
        assertThat(second.getModificationUnwind()).isCloseTo(new BigDecimal("9.2450"), within(new BigDecimal("0.005")));
        assertThat(first.getModificationUnwind().add(second.getModificationUnwind())).isEqualByComparingTo(loss);
    }
}

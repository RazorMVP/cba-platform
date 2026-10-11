package com.cba.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The effective interest method for a loan with an origination fee (IFRS 9 §5.4.1,
 * B5.4.2). The fee is deducted from what is lent, so the bank's net outlay is
 * {@code principal − fee}; the effective rate is the one that discounts the scheduled
 * payments back to that outlay. Interest revenue at that rate exceeds the contractual
 * interest by exactly the fee over the loan's life: that excess is the fee's
 * amortisation, which this class splits across the instalment periods.
 */
final class EffectiveInterest {

    private EffectiveInterest() {}

    /**
     * Each instalment period's share of the fee, in schedule order, summing exactly to
     * {@code fee} (the last period absorbs rounding). All zero when there is no fee.
     */
    static List<BigDecimal> feeAmortisation(BigDecimal principal, BigDecimal fee, LocalDate start,
                                            List<LoanRepaymentSchedule> schedule) {
        List<BigDecimal> shares = new ArrayList<>(schedule.size());
        if (fee.signum() <= 0 || schedule.isEmpty()) {
            schedule.forEach(i -> shares.add(BigDecimal.ZERO));
            return shares;
        }
        double net = principal.subtract(fee).doubleValue();
        double rate = dailyRate(net, start, schedule);

        double carrying = net;
        BigDecimal allocated = BigDecimal.ZERO;
        LocalDate periodStart = start;
        for (int k = 0; k < schedule.size(); k++) {
            LoanRepaymentSchedule i = schedule.get(k);
            long days = ChronoUnit.DAYS.between(periodStart, i.getDueDate());
            double revenue = carrying * (Math.pow(1 + rate, days) - 1);
            BigDecimal share;
            if (k == schedule.size() - 1) {
                share = fee.subtract(allocated);
            } else {
                share = BigDecimal.valueOf(revenue - i.getInterestDue().doubleValue())
                        .setScale(4, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
                allocated = allocated.add(share);
            }
            shares.add(share);
            carrying = carrying + revenue - payment(i).doubleValue();
            periodStart = i.getDueDate();
        }
        return shares;
    }

    /**
     * The daily rate r with Σ payment / (1+r)^days = net, days counted from {@code start}.
     * The present value falls as r rises, so bisection converges; the fee makes the net
     * outlay smaller than the principal, so r is above the contractual rate.
     */
    static double dailyRate(double net, LocalDate start, List<LoanRepaymentSchedule> schedule) {
        double lo = 0, hi = 0.05; // 5% a day is far above any loan's rate
        for (int n = 0; n < 200; n++) {
            double mid = (lo + hi) / 2;
            if (presentValue(mid, start, schedule) > net) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    private static double presentValue(double rate, LocalDate start, List<LoanRepaymentSchedule> schedule) {
        double pv = 0;
        for (LoanRepaymentSchedule i : schedule) {
            pv += payment(i).doubleValue() / Math.pow(1 + rate, ChronoUnit.DAYS.between(start, i.getDueDate()));
        }
        return pv;
    }

    private static BigDecimal payment(LoanRepaymentSchedule i) {
        return i.getPrincipalDue().add(i.getInterestDue()).add(i.getFeesDue());
    }
}

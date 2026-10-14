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
        return feeAmortisation(principal, fee, start, schedule,
                dailyRate(principal.subtract(fee).doubleValue(), start, schedule));
    }

    /** As above, with the effective daily rate already known (computed once at disbursement). */
    static List<BigDecimal> feeAmortisation(BigDecimal principal, BigDecimal fee, LocalDate start,
                                            List<LoanRepaymentSchedule> schedule, double rate) {
        List<BigDecimal> shares = new ArrayList<>(schedule.size());
        if (fee.signum() <= 0 || schedule.isEmpty()) {
            schedule.forEach(i -> shares.add(BigDecimal.ZERO));
            return shares;
        }
        double net = principal.subtract(fee).doubleValue();

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

    /** The contractual payment: interest later given up by a waiver counts, so the rate stays the original one. */
    private static BigDecimal payment(LoanRepaymentSchedule i) {
        return i.getPrincipalDue().add(i.getInterestDue()).add(i.getInterestCancelled()).add(i.getFeesDue());
    }

    // ── Modifications (IFRS 9 §5.4.3) ────────────────────────────────────────

    /** A cash flow of a loan's schedule. */
    record CashFlow(LocalDate due, BigDecimal amount) {}

    /**
     * The loan's original effective daily rate: stored at disbursement, else computed from
     * the contractual schedule (loans disbursed before it was stored). Never recomputed
     * after a modification: modified cash flows are discounted at the original rate.
     */
    static double originalDailyRate(Loan loan) {
        if (loan.getEffectiveDailyRate() != null) return loan.getEffectiveDailyRate();
        BigDecimal lent = loan.getApprovedAmount() != null ? loan.getApprovedAmount() : loan.getPrincipalAmount();
        return dailyRate(lent.subtract(loan.getDeferredFee()).doubleValue(), loan.getDisbursementDate(),
                loan.getRepaymentSchedule());
    }

    /** Σ amount / (1+rate)^days from {@code at} to each due date, to 4 decimals. */
    static BigDecimal presentValue(List<CashFlow> flows, LocalDate at, double rate) {
        double pv = 0;
        for (CashFlow f : flows) {
            pv += f.amount().doubleValue() / Math.pow(1 + rate, ChronoUnit.DAYS.between(at, f.due()));
        }
        return BigDecimal.valueOf(pv).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Spreads the modification discount the loan carries at {@code date} ({@code target}:
     * what is still to unwind, including any from earlier modifications) over the
     * instalments due after {@code date}, so that interest revenue each period is the
     * original effective rate on the modified gross carrying amount (IFRS 9 §5.4.3, IE68–70).
     *
     * <p>After each due date k, the discount still held is T_k = the loan's ledger carrying
     * amount (principal, interest receivable, deferred fee of the later instalments) less
     * G_k, the present value of the remaining modified cash flows at the original rate.
     * A period unwinds T_{k−1} − T_k; T is never negative or rising, and nil after the
     * last instalment, so the shares add up to {@code target} exactly.
     */
    static void spreadModificationDiscount(Loan loan, LocalDate date, double rate, BigDecimal target) {
        List<LoanRepaymentSchedule> later = loan.getRepaymentSchedule().stream()
                .filter(i -> i.getDueDate().isAfter(date)).toList();
        if (later.isEmpty()) return;

        List<CashFlow> flows = later.stream().map(i -> new CashFlow(i.getDueDate(), i.unpaid())).toList();
        double g = presentValue(flows, date, rate).doubleValue();
        BigDecimal previous = target;
        LocalDate from = date;
        for (int k = 0; k < later.size(); k++) {
            LoanRepaymentSchedule i = later.get(k);
            g = g * Math.pow(1 + rate, ChronoUnit.DAYS.between(from, i.getDueDate())) - i.unpaid().doubleValue();
            BigDecimal held = BigDecimal.ZERO;
            if (k < later.size() - 1) {
                BigDecimal ledger = BigDecimal.ZERO;
                for (LoanRepaymentSchedule j : later.subList(k + 1, later.size())) {
                    ledger = ledger.add(j.unpaidPrincipal()).add(j.interestReceivable())
                            .subtract(j.getFeeAmortisation().subtract(j.getFeeAmortised()));
                }
                held = ledger.subtract(BigDecimal.valueOf(g)).setScale(4, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO).min(previous);
            }
            i.setModificationUnwind(i.getModificationUnwound().add(previous.subtract(held)));
            previous = held;
            from = i.getDueDate();
        }
    }
}

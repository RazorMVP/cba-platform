package com.cba.loan;

import com.cba.charge.ChargeDefinition;
import com.cba.charge.LoanCharge;
import com.cba.charge.LoanChargeRepository;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Daily loan income: interest accrual and origination-fee amortisation (IFRS 9 §5.4.1,
 * B5.4.2), days past due, and the non-performing suspense rule (CBN Prudential
 * Guidelines: a loan 90 days or more past due stops recognising interest and penalties
 * as income; they are held in suspense and become income only when received).
 *
 * <p>Interest accrues day by day within each instalment period, pro rata to the period's
 * scheduled interest, so the receivable reaches exactly the instalment's interest on its
 * due date — which is what a repayment of that instalment clears. The fee is released
 * the same way, by each period's effective-interest share ({@link EffectiveInterest}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanAccrualService {

    /** Days past due at which a loan is non-performing: "90 days or more" (CBN); IFRS 9 B5.5.37 backstop. */
    public static final int NON_PERFORMING_DAYS = 90;

    private final LoanRepository loanRepository;
    private final LoanChargeRepository loanChargeRepository;
    private final LoanGlPosting loanGlPosting;

    /**
     * The nightly run for one loan, in its own transaction so one bad loan can't roll
     * back the others: arrears, non-performing status, charges now due, accrual.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runCloseOfBusiness(UUID loanId, LocalDate date) {
        Loan loan = loanRepository.findById(loanId).orElseThrow(() -> CbaException.notFound("Loan", loanId));
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.IN_ARREARS) return;
        bringCurrent(loan, date);
        loanRepository.save(loan);
    }

    /**
     * Brings an open loan up to {@code date} in the caller's transaction: arrears,
     * non-performing status, charges now due, accrual. The nightly run does this, and so
     * does a write-off before it measures what to derecognise.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bringCurrent(Loan loan, LocalDate date) {
        updateArrears(loan, date);
        classify(loan, date);
        recogniseDueCharges(loan, date);
        accrueThrough(loan, date);
    }

    // ── Arrears ──────────────────────────────────────────────────────────────

    /**
     * Days past due from the oldest instalment due before {@code date} and not fully
     * paid (partly paid counts as unpaid), and ACTIVE ↔ IN_ARREARS to match. Returns
     * whether anything changed.
     */
    public static boolean updateArrears(Loan loan, LocalDate date) {
        LocalDate oldest = loan.getRepaymentSchedule().stream()
                .filter(i -> !i.isSettled() && i.getDueDate().isBefore(date))
                .map(LoanRepaymentSchedule::getDueDate)
                .min(LocalDate::compareTo).orElse(null);
        int dpd = oldest == null ? 0 : (int) ChronoUnit.DAYS.between(oldest, date);
        LoanStatus status = oldest == null ? LoanStatus.ACTIVE : LoanStatus.IN_ARREARS;
        boolean changed = dpd != loan.getDaysPastDue() || status != loan.getStatus()
                || !java.util.Objects.equals(oldest, loan.getOverdueSince());
        loan.setDaysPastDue(dpd);
        loan.setOverdueSince(oldest);
        loan.setStatus(status);
        return changed;
    }

    // ── Non-performing ───────────────────────────────────────────────────────

    /**
     * At 90+ days past due the loan becomes non-performing: accrued interest not yet
     * received, and unpaid penalties, leave income for suspense. Below 90 it is
     * performing again; what sits in suspense stays there until the cash arrives.
     */
    void classify(Loan loan, LocalDate date) {
        boolean shouldBeNonPerforming = loan.getDaysPastDue() >= NON_PERFORMING_DAYS;
        if (shouldBeNonPerforming && !loan.isNonPerforming()) {
            BigDecimal unpaidAccrued = loan.getRepaymentSchedule().stream()
                    .map(i -> i.interestReceivable().max(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // Interest suspended in an earlier non-performing spell is already out of income.
            BigDecimal interest = unpaidAccrued.subtract(loan.getInterestInSuspense()).max(BigDecimal.ZERO);
            List<LoanCharge> penalties = loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loan.getId()).stream()
                    .filter(c -> c.isPenalty() && c.getIncomeRecognizedOn() != null && !c.isSuspended()
                            && !c.isPaid() && !c.isWaived() && c.getAmountOutstanding().signum() > 0)
                    .toList();
            loanGlPosting.postSuspension(loan, interest, penalties, date);
            penalties.forEach(c -> {
                c.setSuspended(true);
                loanChargeRepository.save(c);
            });
            loan.setInterestInSuspense(loan.getInterestInSuspense().add(interest));
            loan.setNonPerforming(true);
            loan.setNonPerformingSince(date);
            log.info("Loan {} non-performing at {} dpd: {} interest and {} penalties to suspense",
                    loan.getLoanAccountNumber(), loan.getDaysPastDue(), interest, penalties.size());
        } else if (!shouldBeNonPerforming && loan.isNonPerforming()) {
            loan.setNonPerforming(false);
            loan.setNonPerformingSince(null);
        }
    }

    // ── Charges falling due ──────────────────────────────────────────────────

    /** Fees whose due date has come are income now; penalties on a non-performing loan go to suspense. */
    void recogniseDueCharges(Loan loan, LocalDate date) {
        for (LoanCharge c : loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loan.getId())) {
            if (c.getIncomeRecognizedOn() != null || c.isPaid() || c.isWaived()
                    || c.getChargeTimeType() == ChargeDefinition.ChargeTimeType.DISBURSEMENT
                    || c.getDueForCollectionAsOfDate() == null || c.getDueForCollectionAsOfDate().isAfter(date)
                    || c.getAmountOutstanding().signum() <= 0) {
                continue;
            }
            if (loan.isNonPerforming() && c.isPenalty()) {
                loanGlPosting.postChargeToSuspense(loan, c, c.getAmountOutstanding(), date);
                c.setSuspended(true);
            } else {
                loanGlPosting.postChargeRecognition(loan, c, c.getAmountOutstanding(), date);
            }
            c.setIncomeRecognizedOn(date);
            loanChargeRepository.save(c);
        }
    }

    // ── Accrual ──────────────────────────────────────────────────────────────

    /**
     * Accrues interest and fee amortisation for every day up to {@code date} not yet
     * accrued, in one journal. The first run catches up from the disbursement date.
     * Runs in the caller's transaction (repayment, foreclosure, or the nightly run).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void accrueThrough(Loan loan, LocalDate date) {
        LocalDate disbursed = loan.getDisbursementDate();
        if (disbursed == null) return;
        LocalDate from = loan.getInterestAccruedThrough() != null ? loan.getInterestAccruedThrough() : disbursed;
        if (!date.isAfter(from)) return;

        BigDecimal interest = BigDecimal.ZERO, fee = BigDecimal.ZERO, unwind = BigDecimal.ZERO;
        LocalDate periodStart = disbursed;
        for (LoanRepaymentSchedule i : loan.getRepaymentSchedule()) {
            LocalDate due = i.getDueDate();
            if (!date.isAfter(periodStart)) break;
            BigDecimal interestTarget = accruedBy(i.getInterestDue(), periodStart, due, date);
            BigDecimal addInterest = interestTarget.subtract(i.getInterestAccrued());
            if (addInterest.signum() > 0) {
                i.setInterestAccrued(interestTarget);
                interest = interest.add(addInterest);
            }
            BigDecimal feeTarget = accruedBy(i.getFeeAmortisation(), periodStart, due, date);
            BigDecimal addFee = feeTarget.subtract(i.getFeeAmortised());
            if (addFee.signum() > 0) {
                i.setFeeAmortised(feeTarget);
                fee = fee.add(addFee);
            }
            // The modification discount still to unwind in this period, spread over its
            // days not yet accrued (a waiver mid-period re-spreads from the waiver date).
            BigDecimal toUnwind = i.getModificationUnwind().subtract(i.getModificationUnwound());
            if (toUnwind.signum() > 0) {
                BigDecimal add = accruedBy(toUnwind, periodStart.isAfter(from) ? periodStart : from, due, date);
                if (add.signum() > 0) {
                    i.setModificationUnwound(i.getModificationUnwound().add(add));
                    unwind = unwind.add(add);
                }
            }
            periodStart = due;
        }

        loanGlPosting.postAccrual(loan, interest, fee, loan.isNonPerforming(), date);
        if (unwind.signum() > 0) {
            loanGlPosting.postModificationUnwind(loan, unwind, loan.isNonPerforming(), date);
            loan.setModificationDiscountRemaining(loan.getModificationDiscountRemaining().subtract(unwind));
        }
        if (loan.isNonPerforming()) {
            loan.setInterestInSuspense(loan.getInterestInSuspense().add(interest).add(unwind));
        }
        loan.setDeferredFeeRemaining(loan.getDeferredFeeRemaining().subtract(fee));
        loan.setInterestAccruedThrough(date);
    }

    /**
     * How much of a period's {@code total} has been earned by {@code date}: pro rata to
     * the days elapsed in the period, all of it from the due date on. Rounded down, so
     * accruals never run ahead of the schedule.
     */
    public static BigDecimal accruedBy(BigDecimal total, LocalDate periodStart, LocalDate due, LocalDate date) {
        if (!date.isBefore(due)) return total;
        if (!date.isAfter(periodStart)) return BigDecimal.ZERO;
        long days = ChronoUnit.DAYS.between(periodStart, due);
        long elapsed = ChronoUnit.DAYS.between(periodStart, date);
        return total.multiply(BigDecimal.valueOf(elapsed))
                .divide(BigDecimal.valueOf(days), 4, RoundingMode.DOWN);
    }

    // ── Settlement ───────────────────────────────────────────────────────────

    /**
     * The loan is settled (paid in full or foreclosed): interest received ahead of
     * accrual, the fee still deferred and the modification discount not yet unwound
     * become income now (derecognition), as does interest still in suspense (the cash
     * has all been received).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeOut(Loan loan, LocalDate date) {
        BigDecimal prepaid = BigDecimal.ZERO;
        for (LoanRepaymentSchedule i : loan.getRepaymentSchedule()) {
            BigDecimal ahead = i.interestReceivable().negate();
            if (ahead.signum() > 0) {
                prepaid = prepaid.add(ahead);
                i.setInterestAccrued(i.getInterestAccrued().add(ahead));
            }
            i.setFeeAmortised(i.getFeeAmortisation());
            i.setModificationUnwound(i.getModificationUnwind());
        }
        BigDecimal fee = loan.getDeferredFeeRemaining().max(BigDecimal.ZERO);
        loanGlPosting.postCloseOut(loan, prepaid, fee, date);
        if (loan.getModificationDiscountRemaining().signum() > 0) {
            loanGlPosting.postModificationUnwind(loan, loan.getModificationDiscountRemaining(), false, date);
        }
        if (loan.getInterestInSuspense().signum() > 0) {
            loanGlPosting.postInterestSuspenseRelease(loan, loan.getInterestInSuspense(), date);
        }
        loan.setModificationDiscountRemaining(BigDecimal.ZERO);
        loan.setInterestInSuspense(BigDecimal.ZERO);
        loan.setDeferredFeeRemaining(BigDecimal.ZERO);
        loan.setDaysPastDue(0);
        loan.setOverdueSince(null);
        loan.setNonPerforming(false);
        loan.setNonPerformingSince(null);
    }
}

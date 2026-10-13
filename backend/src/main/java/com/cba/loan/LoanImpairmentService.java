package com.cba.loan;

import com.cba.accounting.ProvisioningCriteria;
import com.cba.accounting.ProvisioningCriteriaDefinition;
import com.cba.accounting.ProvisioningCriteriaRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * IFRS 9 expected credit losses on loans, and the CBN prudential provision beside them.
 *
 * <p><b>Stage</b> (IFRS 9 §5.5.3, §5.5.5, §5.5.11, B5.5.37): 1 when up to 30 days past
 * due (12-month losses), 2 when more than 30 (lifetime losses), 3 when more than 90 or
 * non-performing (credit-impaired, lifetime losses). <b>ECL</b> = EAD × PD × LGD: EAD is
 * the gross carrying amount, PD comes from the risk team's {@link EclPdRate} table by
 * stage and days past due, LGD from the loan product. The allowance moves to the new ECL
 * every measurement; the change is an impairment gain or loss (§5.5.8).
 *
 * <p><b>Prudential provision</b> (CBN Prudential Guidelines), from the active
 * provisioning criteria's days-past-due bands: a performing loan carries the band's
 * general provision on its principal; a non-performing one carries 100% of principal
 * more than 90 days overdue plus the band's percentage of the rest of the principal. The
 * regulatory risk reserve compares the total with the IFRS allowance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanImpairmentService {

    /** More than this many days past due: significant increase in credit risk (IFRS 9 §5.5.11). */
    public static final int STAGE_2_DAYS = 30;
    /** More than this many days past due: default, credit-impaired (IFRS 9 B5.5.37; CBN). */
    public static final int STAGE_3_DAYS = 90;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final LoanRepository loanRepository;
    private final LoanChargeRepository loanChargeRepository;
    private final EclPdRateRepository pdRateRepository;
    private final ProvisioningCriteriaRepository provisioningCriteriaRepository;
    private final LoanGlPosting loanGlPosting;

    /** The figures behind one measurement. */
    public record Measurement(int stage, BigDecimal exposure, BigDecimal pdPercentage, BigDecimal lgdPercentage,
                              BigDecimal expectedCreditLoss, BigDecimal prudentialProvision) {}

    /** The nightly measurement of one open loan, in its own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void measure(UUID loanId, LocalDate date) {
        Loan loan = loanRepository.findById(loanId).orElseThrow(() -> CbaException.notFound("Loan", loanId));
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.IN_ARREARS) return;
        remeasure(loan, date);
        loanRepository.save(loan);
    }

    /**
     * Stages the loan, moves its allowance to the expected credit loss (posting the
     * difference) and refreshes the prudential provision. Runs in the caller's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Measurement remeasure(Loan loan, LocalDate date) {
        List<LoanCharge> charges = loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loan.getId());
        int stage = stage(loan);
        BigDecimal exposure = exposure(loan, charges);
        BigDecimal pd = probabilityOfDefault(stage, loan.getDaysPastDue());
        BigDecimal lgd = loan.getProduct().getLgdPercentage();
        BigDecimal ecl = expectedCreditLoss(exposure, pd, lgd);

        loanGlPosting.postImpairment(loan, ecl.subtract(loan.getLossAllowance()), date);
        loan.setLossAllowance(ecl);
        loan.setEclStage(stage);
        loan.setEclMeasuredOn(date);

        BigDecimal prudential = prudentialProvision(loan, date, activeCriteria());
        loan.setPrudentialProvision(prudential);
        BigDecimal principal = loan.getOutstandingBalance();
        boolean fullyProvided = principal.signum() > 0 && prudential.compareTo(principal) >= 0;
        if (!fullyProvided) {
            loan.setFullyProvidedSince(null);
        } else if (loan.getFullyProvidedSince() == null) {
            loan.setFullyProvidedSince(date);
        }
        return new Measurement(stage, exposure, pd, lgd, ecl, prudential);
    }

    /** The loan is settled: whatever allowance it still carries is released. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Loan loan, LocalDate date) {
        loanGlPosting.postImpairment(loan, loan.getLossAllowance().negate(), date);
        loan.setLossAllowance(BigDecimal.ZERO);
        loan.setEclStage(null);
        loan.setEclMeasuredOn(date);
        loan.setPrudentialProvision(BigDecimal.ZERO);
        loan.setFullyProvidedSince(null);
    }

    // ── Pieces of the measurement ────────────────────────────────────────

    static int stage(Loan loan) {
        if (loan.isNonPerforming() || loan.getDaysPastDue() > STAGE_3_DAYS) return 3;
        if (loan.getDaysPastDue() > STAGE_2_DAYS) return 2;
        return 1;
    }

    /**
     * Gross carrying amount (exposure at default): principal, plus interest accrued and
     * not received, plus charges recognised and unpaid, less the deferred origination fee
     * and less what is held in suspense (never income). Never negative.
     */
    static BigDecimal exposure(Loan loan, List<LoanCharge> charges) {
        BigDecimal interest = interestReceivable(loan);
        BigDecimal fees = BigDecimal.ZERO, suspendedCharges = BigDecimal.ZERO;
        for (LoanCharge c : charges) {
            if (c.getIncomeRecognizedOn() == null || c.isPaid() || c.isWaived()) continue;
            fees = fees.add(c.getAmountOutstanding());
            if (c.isSuspended()) suspendedCharges = suspendedCharges.add(c.getAmountOutstanding());
        }
        return loan.getOutstandingBalance().add(interest).add(fees)
                .subtract(loan.getDeferredFeeRemaining())
                .subtract(loan.getInterestInSuspense()).subtract(suspendedCharges)
                .max(BigDecimal.ZERO);
    }

    /** The loan's interest receivable: accrued less received (negative when paid ahead). */
    static BigDecimal interestReceivable(Loan loan) {
        return loan.getRepaymentSchedule().stream()
                .map(i -> i.getInterestAccrued().subtract(i.getInterestPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal expectedCreditLoss(BigDecimal exposure, BigDecimal pdPercentage, BigDecimal lgdPercentage) {
        return exposure.multiply(pdPercentage).multiply(lgdPercentage)
                .divide(HUNDRED.multiply(HUNDRED), 4, RoundingMode.HALF_UP);
    }

    /** PD for the stage and days past due: the band that holds them, else the stage's first band. */
    BigDecimal probabilityOfDefault(int stage, int daysPastDue) {
        List<EclPdRate> rates = pdRateRepository.findByStageOrderByMinDpdAsc(stage);
        if (rates.isEmpty()) {
            throw CbaException.badRequest("ECL_PD_NOT_CONFIGURED", "No probability of default is set for stage " + stage);
        }
        return rates.stream()
                .filter(r -> daysPastDue >= r.getMinDpd() && daysPastDue <= r.getMaxDpd())
                .findFirst().orElse(rates.get(0))
                .getPdPercentage();
    }

    /** The one active provisioning criteria: the CBN prudential bands. */
    ProvisioningCriteria activeCriteria() {
        List<ProvisioningCriteria> active = provisioningCriteriaRepository.findAll().stream()
                .filter(ProvisioningCriteria::isActive).toList();
        if (active.isEmpty()) {
            throw CbaException.badRequest("PROVISIONING_CRITERIA_NOT_CONFIGURED",
                    "No active provisioning criteria: the CBN prudential provision cannot be measured");
        }
        if (active.size() > 1) {
            throw CbaException.badRequest("PROVISIONING_CRITERIA_AMBIGUOUS",
                    active.size() + " provisioning criteria are active; deactivate all but one");
        }
        return active.get(0);
    }

    /**
     * The CBN prudential provision. Performing: the band's percentage of principal.
     * Non-performing: 100% of principal more than 90 days overdue, plus the band's
     * percentage of the rest of the principal.
     */
    static BigDecimal prudentialProvision(Loan loan, LocalDate date, ProvisioningCriteria criteria) {
        int dpd = loan.getDaysPastDue();
        ProvisioningCriteriaDefinition band = criteria.getDefinitions().stream()
                .filter(d -> dpd >= d.getMinAge() && dpd <= d.getMaxAge())
                .findFirst()
                .or(() -> criteria.getDefinitions().stream()
                        .filter(d -> d.getMinAge() <= dpd)
                        .max(Comparator.comparingInt(ProvisioningCriteriaDefinition::getMinAge)))
                .orElseThrow(() -> CbaException.badRequest("PROVISIONING_BAND_MISSING",
                        "Provisioning criteria " + criteria.getCriteriaName() + " has no band for " + dpd + " days past due"));
        BigDecimal rate = band.getProvisionPercentage().divide(HUNDRED, 6, RoundingMode.HALF_UP);
        BigDecimal principal = loan.getOutstandingBalance();
        if (!loan.isNonPerforming() && dpd <= STAGE_3_DAYS) {
            return principal.multiply(rate).setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal overdue = loan.getRepaymentSchedule().stream()
                .filter(i -> i.getStatus() != LoanRepaymentSchedule.InstallmentStatus.PAID
                        && i.getDueDate().isBefore(date.minusDays(STAGE_3_DAYS)))
                .map(i -> i.getPrincipalDue().subtract(i.getPrincipalPaid()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(principal);
        return overdue.add(principal.subtract(overdue).multiply(rate)).setScale(4, RoundingMode.HALF_UP);
    }
}

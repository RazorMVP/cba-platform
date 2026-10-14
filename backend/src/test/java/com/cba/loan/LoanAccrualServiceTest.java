package com.cba.loan;

import com.cba.charge.LoanCharge;
import com.cba.charge.LoanChargeRepository;
import com.cba.product.LoanProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanAccrualService — accrual, days past due, suspense")
class LoanAccrualServiceTest {

    @Mock LoanRepository loanRepository;
    @Mock LoanChargeRepository loanChargeRepository;
    @Mock LoanGlPosting loanGlPosting;
    @InjectMocks LoanAccrualService service;

    static final LocalDate DISBURSED = LocalDate.of(2026, 1, 1);
    Loan loan;

    @BeforeEach
    void setUp() {
        LoanProduct product = new LoanProduct();
        product.setCurrencyCode("USD");
        loan = new Loan();
        loan.setId(UUID.randomUUID());
        loan.setLoanAccountNumber("001-LN-TEST");
        loan.setProduct(product);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursementDate(DISBURSED);
        loan.setInterestAccruedThrough(DISBURSED);
        loan.setRepaymentSchedule(new ArrayList<>());
        // Two 31-day-ish periods: Jan 1 → Feb 1 (31 days), Feb 1 → Mar 1 (28 days).
        loan.getRepaymentSchedule().add(installment(1, LocalDate.of(2026, 2, 1), "310.00", "12.40"));
        loan.getRepaymentSchedule().add(installment(2, LocalDate.of(2026, 3, 1), "280.00", "5.60"));
    }

    @Test
    @DisplayName("accruedBy: pro rata by days, rounded down, everything from the due date")
    void accruedBy() {
        LocalDate due = LocalDate.of(2026, 2, 1);
        assertThat(LoanAccrualService.accruedBy(new BigDecimal("310.00"), DISBURSED, due, DISBURSED)).isEqualByComparingTo("0");
        assertThat(LoanAccrualService.accruedBy(new BigDecimal("310.00"), DISBURSED, due, LocalDate.of(2026, 1, 11)))
                .isEqualByComparingTo("100.00");
        assertThat(LoanAccrualService.accruedBy(new BigDecimal("10.00"), DISBURSED, due, LocalDate.of(2026, 1, 2)))
                .isEqualByComparingTo("0.3225"); // 10/31 = 0.32258… rounded down
        assertThat(LoanAccrualService.accruedBy(new BigDecimal("310.00"), DISBURSED, due, due.plusDays(5)))
                .isEqualByComparingTo("310.00");
    }

    @Test
    @DisplayName("catch-up accrual across periods posts one journal and is idempotent")
    void accrueThrough_catchUp_thenNoOp() {
        service.accrueThrough(loan, LocalDate.of(2026, 2, 15)); // all of period 1 + 14 of 28 days of period 2

        verify(loanGlPosting).postAccrual(loan, new BigDecimal("450.0000"), BigDecimal.ZERO, false, LocalDate.of(2026, 2, 15));
        assertThat(loan.getRepaymentSchedule().get(0).getInterestAccrued()).isEqualByComparingTo("310.00");
        assertThat(loan.getRepaymentSchedule().get(1).getInterestAccrued()).isEqualByComparingTo("140.00");
        assertThat(loan.getInterestAccruedThrough()).isEqualTo(LocalDate.of(2026, 2, 15));

        service.accrueThrough(loan, LocalDate.of(2026, 2, 15));
        verifyNoMoreInteractions(loanGlPosting);
    }

    @Test
    @DisplayName("fee amortisation is released alongside interest and reduces the deferred balance")
    void accrueThrough_releasesFee() {
        loan.getRepaymentSchedule().get(0).setFeeAmortisation(new BigDecimal("31.00"));
        loan.setDeferredFeeRemaining(new BigDecimal("50.00"));

        service.accrueThrough(loan, LocalDate.of(2026, 1, 11));

        verify(loanGlPosting).postAccrual(eq(loan), eq(new BigDecimal("100.0000")), eq(new BigDecimal("10.0000")),
                eq(false), any());
        assertThat(loan.getDeferredFeeRemaining()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("days past due count from the oldest unpaid instalment — a part-paid one included")
    void updateArrears_partlyPaidCounts() {
        LoanRepaymentSchedule first = loan.getRepaymentSchedule().get(0);
        first.setStatus(LoanRepaymentSchedule.InstallmentStatus.PARTIALLY_PAID);

        boolean changed = LoanAccrualService.updateArrears(loan, LocalDate.of(2026, 2, 11));

        assertThat(changed).isTrue();
        assertThat(loan.getDaysPastDue()).isEqualTo(10);
        assertThat(loan.getOverdueSince()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.IN_ARREARS);
    }

    @Test
    @DisplayName("at 90 days past due accrued unpaid interest moves to suspense; later accruals go there too")
    void classify_nonPerforming_suspendsThenAccruesToSuspense() {
        loan.getRepaymentSchedule().get(0).setInterestAccrued(new BigDecimal("310.00"));
        loan.getRepaymentSchedule().get(0).setInterestPaid(new BigDecimal("10.00"));
        loan.setDaysPastDue(90);
        when(loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loan.getId())).thenReturn(List.of());

        service.classify(loan, LocalDate.of(2026, 5, 2));

        verify(loanGlPosting).postSuspension(loan, new BigDecimal("300.00"), List.of(), LocalDate.of(2026, 5, 2));
        assertThat(loan.isNonPerforming()).isTrue();
        assertThat(loan.getInterestInSuspense()).isEqualByComparingTo("300.00");

        loan.setInterestAccruedThrough(LocalDate.of(2026, 2, 1));
        service.accrueThrough(loan, LocalDate.of(2026, 2, 15));
        verify(loanGlPosting).postAccrual(eq(loan), eq(new BigDecimal("140.0000")), any(), eq(true), any());
        assertThat(loan.getInterestInSuspense()).isEqualByComparingTo("440.00");
    }

    @Test
    @DisplayName("an unpaid penalty is suspended too; dropping below 90 days keeps suspense until cash arrives")
    void classify_penaltySuspended_thenCureKeepsSuspense() {
        LoanCharge penalty = new LoanCharge();
        penalty.setPenalty(true);
        penalty.setIncomeRecognizedOn(LocalDate.of(2026, 3, 1));
        penalty.setAmount(new BigDecimal("25.00"));
        penalty.setAmountOutstanding(new BigDecimal("25.00"));
        loan.setDaysPastDue(95);
        when(loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loan.getId())).thenReturn(List.of(penalty));

        service.classify(loan, LocalDate.of(2026, 5, 7));

        assertThat(penalty.isSuspended()).isTrue();
        verify(loanGlPosting).postSuspension(loan, BigDecimal.ZERO, List.of(penalty), LocalDate.of(2026, 5, 7));

        loan.setDaysPastDue(10);
        loan.setInterestInSuspense(new BigDecimal("40.00"));
        service.classify(loan, LocalDate.of(2026, 6, 1));
        assertThat(loan.isNonPerforming()).isFalse();
        assertThat(loan.getInterestInSuspense()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("settling recognises interest received ahead of accrual and the deferred fee")
    void closeOut_recognisesPrepaidInterestAndFee() {
        LoanRepaymentSchedule second = loan.getRepaymentSchedule().get(1);
        second.setInterestPaid(new BigDecimal("280.00"));
        second.setInterestAccrued(new BigDecimal("100.00"));
        loan.setDeferredFeeRemaining(new BigDecimal("12.50"));

        service.closeOut(loan, LocalDate.of(2026, 2, 10));

        verify(loanGlPosting).postCloseOut(loan, new BigDecimal("180.00"), new BigDecimal("12.50"), LocalDate.of(2026, 2, 10));
        assertThat(loan.getDeferredFeeRemaining()).isEqualByComparingTo("0");
        assertThat(second.getInterestAccrued()).isEqualByComparingTo("280.00");
    }

    @Test
    @DisplayName("the modification discount unwinds pro rata from the last accrual, all of it by the due date")
    void modificationDiscount_unwinds() {
        // A waiver on Feb 10 left 8.40 to unwind in period 2 (Feb 1 → Mar 1) and nothing else.
        loan.setInterestAccruedThrough(LocalDate.of(2026, 2, 10));
        loan.getRepaymentSchedule().get(0).setInterestAccrued(new BigDecimal("310.00"));
        loan.getRepaymentSchedule().get(1).setInterestDue(new BigDecimal("90.00"));
        loan.getRepaymentSchedule().get(1).setInterestAccrued(new BigDecimal("90.00"));
        loan.getRepaymentSchedule().get(1).setModificationUnwind(new BigDecimal("8.40"));
        loan.setModificationDiscountRemaining(new BigDecimal("8.40"));

        service.accrueThrough(loan, LocalDate.of(2026, 2, 17)); // 7 of the 19 days left
        verify(loanGlPosting).postModificationUnwind(loan, new BigDecimal("3.0947"), false, LocalDate.of(2026, 2, 17));
        assertThat(loan.getModificationDiscountRemaining()).isEqualByComparingTo("5.3053");

        service.accrueThrough(loan, LocalDate.of(2026, 3, 5)); // past the due date: the rest
        verify(loanGlPosting).postModificationUnwind(loan, new BigDecimal("5.3053"), false, LocalDate.of(2026, 3, 5));
        assertThat(loan.getModificationDiscountRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getRepaymentSchedule().get(1).getModificationUnwound()).isEqualByComparingTo("8.40");
    }

    @Test
    @DisplayName("on a non-performing loan the unwind goes to suspense, like interest")
    void modificationDiscount_toSuspense() {
        loan.setInterestAccruedThrough(LocalDate.of(2026, 2, 1));
        loan.getRepaymentSchedule().get(0).setInterestAccrued(new BigDecimal("310.00"));
        loan.getRepaymentSchedule().get(1).setInterestDue(BigDecimal.ZERO);
        loan.getRepaymentSchedule().get(1).setModificationUnwind(new BigDecimal("2.80"));
        loan.setModificationDiscountRemaining(new BigDecimal("2.80"));
        loan.setNonPerforming(true);

        service.accrueThrough(loan, LocalDate.of(2026, 3, 1));

        verify(loanGlPosting).postModificationUnwind(loan, new BigDecimal("2.80"), true, LocalDate.of(2026, 3, 1));
        assertThat(loan.getInterestInSuspense()).isEqualByComparingTo("2.80");
    }

    @Test
    @DisplayName("settling releases the discount not yet unwound and any interest left in suspense")
    void closeOut_releasesDiscountAndSuspense() {
        loan.getRepaymentSchedule().get(1).setModificationUnwind(new BigDecimal("6.00"));
        loan.getRepaymentSchedule().get(1).setModificationUnwound(new BigDecimal("2.00"));
        loan.setModificationDiscountRemaining(new BigDecimal("4.00"));
        loan.setInterestInSuspense(new BigDecimal("1.50"));

        service.closeOut(loan, LocalDate.of(2026, 2, 10));

        verify(loanGlPosting).postModificationUnwind(loan, new BigDecimal("4.00"), false, LocalDate.of(2026, 2, 10));
        verify(loanGlPosting).postInterestSuspenseRelease(loan, new BigDecimal("1.50"), LocalDate.of(2026, 2, 10));
        assertThat(loan.getModificationDiscountRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getInterestInSuspense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getRepaymentSchedule().get(1).getModificationUnwound()).isEqualByComparingTo("6.00");
    }

    @Test
    @DisplayName("waived interest is neither receivable nor overdue")
    void waivedInterest_notOverdueNorSuspended() {
        LoanRepaymentSchedule first = loan.getRepaymentSchedule().get(0);
        first.setPrincipalPaid(new BigDecimal("12.40"));
        first.setInterestAccrued(new BigDecimal("310.00"));
        first.setInterestWaived(new BigDecimal("310.00"));
        first.refreshStatus(LocalDate.of(2026, 2, 5));

        assertThat(first.getStatus()).isEqualTo(LoanRepaymentSchedule.InstallmentStatus.WAIVED);
        assertThat(LoanAccrualService.updateArrears(loan, LocalDate.of(2026, 2, 20))).isFalse();
        assertThat(loan.getDaysPastDue()).isZero();
        assertThat(first.interestReceivable()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private LoanRepaymentSchedule installment(int no, LocalDate due, String interest, String principal) {
        LoanRepaymentSchedule s = new LoanRepaymentSchedule();
        s.setLoan(loan);
        s.setInstallmentNo(no);
        s.setDueDate(due);
        s.setInterestDue(new BigDecimal(interest));
        s.setPrincipalDue(new BigDecimal(principal));
        s.setFeesDue(BigDecimal.ZERO);
        s.setTotalDue(new BigDecimal(interest).add(new BigDecimal(principal)));
        return s;
    }
}

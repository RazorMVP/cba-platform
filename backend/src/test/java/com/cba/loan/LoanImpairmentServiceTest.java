package com.cba.loan;

import com.cba.accounting.ProvisioningCriteria;
import com.cba.accounting.ProvisioningCriteriaDefinition;
import com.cba.accounting.ProvisioningCriteriaRepository;
import com.cba.charge.LoanCharge;
import com.cba.charge.LoanChargeRepository;
import com.cba.common.exception.CbaException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanImpairmentService — IFRS 9 stages, ECL, CBN prudential provision")
class LoanImpairmentServiceTest {

    @Mock LoanRepository loanRepository;
    @Mock LoanChargeRepository loanChargeRepository;
    @Mock EclPdRateRepository pdRateRepository;
    @Mock ProvisioningCriteriaRepository provisioningCriteriaRepository;
    @Mock LoanGlPosting loanGlPosting;
    @InjectMocks LoanImpairmentService service;

    static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    Loan loan;

    @BeforeEach
    void setUp() {
        LoanProduct product = new LoanProduct();
        product.setCurrencyCode("USD");
        product.setLgdPercentage(new BigDecimal("45.00"));
        loan = new Loan();
        loan.setId(UUID.randomUUID());
        loan.setLoanAccountNumber("001-LN-ECL");
        loan.setProduct(product);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setOutstandingBalance(new BigDecimal("1000.00"));
        loan.setRepaymentSchedule(new ArrayList<>());
    }

    @Test
    @DisplayName("stage: 1 up to 30 dpd, 2 above 30, 3 above 90 or non-performing")
    void stage() {
        assertThat(stageAt(0, false)).isEqualTo(1);
        assertThat(stageAt(30, false)).isEqualTo(1);
        assertThat(stageAt(31, false)).isEqualTo(2);
        assertThat(stageAt(90, false)).isEqualTo(2);
        assertThat(stageAt(90, true)).isEqualTo(3);
        assertThat(stageAt(91, false)).isEqualTo(3);
    }

    @Test
    @DisplayName("exposure = principal + interest receivable + recognised charges − deferred fee − suspense")
    void exposure() {
        LoanRepaymentSchedule i = installment(1, TODAY.minusDays(10), "1000.00");
        i.setInterestAccrued(new BigDecimal("50.00"));
        i.setInterestPaid(new BigDecimal("10.00"));
        loan.getRepaymentSchedule().add(i);
        loan.setDeferredFeeRemaining(new BigDecimal("15.00"));
        loan.setInterestInSuspense(new BigDecimal("25.00"));
        LoanCharge fee = charge("20.00", false);
        LoanCharge penalty = charge("30.00", true);
        LoanCharge notYetDue = charge("99.00", false);
        notYetDue.setIncomeRecognizedOn(null);

        // 1000 + 40 + (20 + 30) − 15 − (25 + 30) = 1020
        assertThat(LoanImpairmentService.exposure(loan, List.of(fee, penalty, notYetDue))).isEqualByComparingTo("1020.00");
    }

    @Test
    @DisplayName("ECL = EAD × PD × LGD")
    void expectedCreditLoss() {
        assertThat(LoanImpairmentService.expectedCreditLoss(new BigDecimal("1020.00"), new BigDecimal("20"),
                new BigDecimal("45"))).isEqualByComparingTo("91.8000");
    }

    @Test
    @DisplayName("remeasure raises the allowance to the ECL and records stage and prudential provision")
    void remeasure_increase() {
        loan.setDaysPastDue(40);
        loan.setLossAllowance(new BigDecimal("50.00"));
        when(pdRateRepository.findByStageOrderByMinDpdAsc(2)).thenReturn(List.of(pd(2, 31, 90, "20")));
        when(provisioningCriteriaRepository.findAll()).thenReturn(List.of(cbn()));

        LoanImpairmentService.Measurement m = service.remeasure(loan, TODAY);

        // 1000 × 20% × 45% = 90
        assertThat(m.expectedCreditLoss()).isEqualByComparingTo("90.00");
        verify(loanGlPosting).postImpairment(loan, new BigDecimal("40.0000"), TODAY);
        assertThat(loan.getLossAllowance()).isEqualByComparingTo("90.00");
        assertThat(loan.getEclStage()).isEqualTo(2);
        assertThat(loan.getEclMeasuredOn()).isEqualTo(TODAY);
        // Performing band: 2% of principal.
        assertThat(loan.getPrudentialProvision()).isEqualByComparingTo("20.00");
        assertThat(loan.getFullyProvidedSince()).isNull();
    }

    @Test
    @DisplayName("remeasure releases allowance no longer needed (impairment gain)")
    void remeasure_decrease() {
        loan.setLossAllowance(new BigDecimal("200.00"));
        when(pdRateRepository.findByStageOrderByMinDpdAsc(1)).thenReturn(List.of(pd(1, 0, 30, "2")));
        when(provisioningCriteriaRepository.findAll()).thenReturn(List.of(cbn()));

        service.remeasure(loan, TODAY);

        // 1000 × 2% × 45% = 9
        verify(loanGlPosting).postImpairment(loan, new BigDecimal("-191.0000"), TODAY);
        assertThat(loan.getLossAllowance()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("PD: the band holding the days past due, else the stage's first band; none at all → 400")
    void probabilityOfDefault() {
        when(pdRateRepository.findByStageOrderByMinDpdAsc(3)).thenReturn(List.of(pd(3, 91, 180, "80"), pd(3, 181, 99999, "100")));
        assertThat(service.probabilityOfDefault(3, 200)).isEqualByComparingTo("100");
        assertThat(service.probabilityOfDefault(3, 90)).isEqualByComparingTo("80"); // non-performing at 90: first band
        when(pdRateRepository.findByStageOrderByMinDpdAsc(2)).thenReturn(List.of());
        assertThatThrownBy(() -> service.probabilityOfDefault(2, 40))
                .isInstanceOf(CbaException.class).hasMessageContaining("stage 2");
    }

    @Test
    @DisplayName("CBN non-performing: 100% of principal >90 days overdue + band % of the rest")
    void prudential_nonPerforming() {
        loan.setDaysPastDue(200);
        loan.setNonPerforming(true);
        loan.getRepaymentSchedule().add(installment(1, TODAY.minusDays(200), "400.00")); // > 90 days overdue
        loan.getRepaymentSchedule().add(installment(2, TODAY.minusDays(60), "300.00"));  // overdue < 90 days
        loan.getRepaymentSchedule().add(installment(3, TODAY.plusDays(30), "300.00"));   // not yet due

        // DOUBTFUL 50%: 400 + 50% × 600 = 700
        assertThat(LoanImpairmentService.prudentialProvision(loan, TODAY, cbn())).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("fully provided: dated on the first measurement that covers the principal, kept, cleared when it no longer does")
    void fullyProvidedSince() {
        loan.setDaysPastDue(400);
        loan.setNonPerforming(true);
        when(pdRateRepository.findByStageOrderByMinDpdAsc(3)).thenReturn(List.of(pd(3, 91, 99999, "100")));
        when(provisioningCriteriaRepository.findAll()).thenReturn(List.of(cbn()));

        service.remeasure(loan, TODAY);
        assertThat(loan.getPrudentialProvision()).isEqualByComparingTo("1000.00"); // LOST 100%
        assertThat(loan.getFullyProvidedSince()).isEqualTo(TODAY);

        service.remeasure(loan, TODAY.plusDays(1));
        assertThat(loan.getFullyProvidedSince()).isEqualTo(TODAY);

        loan.setDaysPastDue(150);
        service.remeasure(loan, TODAY.plusDays(2));
        assertThat(loan.getFullyProvidedSince()).isNull();
    }

    @Test
    @DisplayName("exactly one provisioning criteria must be active")
    void activeCriteria() {
        ProvisioningCriteria inactive = cbn();
        inactive.setActive(false);
        when(provisioningCriteriaRepository.findAll()).thenReturn(List.of(inactive));
        assertThatThrownBy(service::activeCriteria).isInstanceOf(CbaException.class).hasMessageContaining("No active");

        when(provisioningCriteriaRepository.findAll()).thenReturn(List.of(cbn(), cbn()));
        assertThatThrownBy(service::activeCriteria).isInstanceOf(CbaException.class).hasMessageContaining("2 provisioning");
    }

    @Test
    @DisplayName("release on settlement: the whole allowance comes back")
    void release() {
        loan.setLossAllowance(new BigDecimal("90.00"));
        loan.setEclStage(2);
        loan.setPrudentialProvision(new BigDecimal("20.00"));

        service.release(loan, TODAY);

        verify(loanGlPosting).postImpairment(loan, new BigDecimal("-90.00"), TODAY);
        assertThat(loan.getLossAllowance()).isEqualByComparingTo("0");
        assertThat(loan.getEclStage()).isNull();
        assertThat(loan.getPrudentialProvision()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("the nightly measurement skips loans that aren't open")
    void measure_skipsClosedLoans() {
        loan.setStatus(LoanStatus.WRITTEN_OFF);
        when(loanRepository.findById(loan.getId())).thenReturn(java.util.Optional.of(loan));

        service.measure(loan.getId(), TODAY);

        verifyNoInteractions(loanGlPosting);
        verify(loanRepository, never()).save(any());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    private int stageAt(int dpd, boolean nonPerforming) {
        loan.setDaysPastDue(dpd);
        loan.setNonPerforming(nonPerforming);
        return LoanImpairmentService.stage(loan);
    }

    private LoanRepaymentSchedule installment(int no, LocalDate due, String principal) {
        LoanRepaymentSchedule s = new LoanRepaymentSchedule();
        s.setLoan(loan);
        s.setInstallmentNo(no);
        s.setDueDate(due);
        s.setPrincipalDue(new BigDecimal(principal));
        s.setInterestDue(BigDecimal.ZERO);
        s.setFeesDue(BigDecimal.ZERO);
        s.setTotalDue(new BigDecimal(principal));
        return s;
    }

    private static LoanCharge charge(String amount, boolean suspendedPenalty) {
        LoanCharge c = new LoanCharge();
        c.setAmount(new BigDecimal(amount));
        c.setAmountOutstanding(new BigDecimal(amount));
        c.setIncomeRecognizedOn(TODAY.minusDays(5));
        c.setPenalty(suspendedPenalty);
        c.setSuspended(suspendedPenalty);
        return c;
    }

    private static EclPdRate pd(int stage, int min, int max, String pct) {
        return new EclPdRate(stage, min, max, new BigDecimal(pct), null);
    }

    private static ProvisioningCriteria cbn() {
        ProvisioningCriteria c = new ProvisioningCriteria();
        c.setCriteriaName("CBN Prudential");
        c.setActive(true);
        c.getDefinitions().add(band(c, "PERFORMING", 0, 90, "2.00"));
        c.getDefinitions().add(band(c, "SUB_STANDARD", 91, 180, "10.00"));
        c.getDefinitions().add(band(c, "DOUBTFUL", 181, 360, "50.00"));
        c.getDefinitions().add(band(c, "LOST", 361, 99999, "100.00"));
        return c;
    }

    private static ProvisioningCriteriaDefinition band(ProvisioningCriteria c, String name, int min, int max, String pct) {
        ProvisioningCriteriaDefinition d = new ProvisioningCriteriaDefinition();
        d.setCriteria(c);
        d.setCategoryName(name);
        d.setMinAge(min);
        d.setMaxAge(max);
        d.setProvisionPercentage(new BigDecimal(pct));
        return d;
    }
}

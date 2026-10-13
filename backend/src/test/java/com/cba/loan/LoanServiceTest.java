package com.cba.loan;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import com.cba.account.AccountType;
import com.cba.account.Transaction;
import com.cba.account.TransactionRepository;
import com.cba.audit.AuditLogService;
import com.cba.charge.LoanCharge;
import com.cba.charge.LoanChargeRepository;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.customer.KycStatus;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanRepaymentRequest;
import com.cba.loan.dto.LoanRepaymentResponse;
import com.cba.loan.dto.LoanResponse;
import com.cba.loan.dto.WriteOffRequest;
import com.cba.loan.dto.ForecloseRequest;
import com.cba.loan.dto.WaiveInterestRequest;
import com.cba.product.LoanProduct;
import com.cba.product.LoanProductRepository;
import com.cba.system.GlobalConfiguration;
import com.cba.system.GlobalConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanService — unit tests")
class LoanServiceTest {

    @Mock LoanRepository loanRepository;
    @Mock CustomerRepository customerRepository;
    @Mock LoanProductRepository loanProductRepository;
    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock RepaymentScheduleEngine scheduleEngine;
    @Mock AuditLogService auditLogService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock LoanGlPosting loanGlPosting;
    @Mock LoanChargeRepository loanChargeRepository;
    @Mock LoanAccrualService loanAccrualService;
    @Mock LoanImpairmentService loanImpairmentService;
    @Mock GlobalConfigurationRepository globalConfigRepository;

    @InjectMocks LoanService loanService;

    private UUID customerId;
    private UUID productId;
    private UUID accountId;
    private UUID loanId;
    private Customer activeCustomer;
    private LoanProduct loanProduct;
    private Account activeAccount;
    private Loan activeLoan;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        loanId = UUID.randomUUID();

        activeCustomer = new Customer();
        activeCustomer.setId(customerId);
        activeCustomer.setKycStatus(KycStatus.ACTIVE);

        loanProduct = new LoanProduct();
        loanProduct.setId(productId);
        loanProduct.setName("Personal Loan");
        loanProduct.setShortName("PERS");
        loanProduct.setMinPrincipal(new BigDecimal("1000.00"));
        loanProduct.setMaxPrincipal(new BigDecimal("50000.00"));
        loanProduct.setMinInterestRate(new BigDecimal("5.00"));
        loanProduct.setMaxInterestRate(new BigDecimal("25.00"));
        loanProduct.setDefaultInterestRate(new BigDecimal("12.00"));
        loanProduct.setMinTermMonths(6);
        loanProduct.setMaxTermMonths(60);
        loanProduct.setNumberOfRepayments(12);

        activeAccount = new Account();
        activeAccount.setId(accountId);
        activeAccount.setStatus(AccountStatus.ACTIVE);
        activeAccount.setBalance(new BigDecimal("5000.00"));
        activeAccount.setCurrencyCode("USD");

        activeLoan = new Loan();
        activeLoan.setId(loanId);
        activeLoan.setLoanAccountNumber("LN-001-0000001");
        activeLoan.setCustomer(activeCustomer);
        activeLoan.setProduct(loanProduct);
        activeLoan.setLinkedAccount(activeAccount);
        activeLoan.setPrincipalAmount(new BigDecimal("10000.00"));
        activeLoan.setApprovedAmount(new BigDecimal("10000.00"));
        activeLoan.setInterestRate(new BigDecimal("12.00"));
        activeLoan.setTermMonths(12);
        activeLoan.setStatus(LoanStatus.ACTIVE);
        activeLoan.setOutstandingBalance(new BigDecimal("10000.00"));
        activeLoan.setRepaymentSchedule(new ArrayList<>());
    }

    @Nested
    @DisplayName("applyForLoan")
    class ApplyForLoan {

        @Test
        @DisplayName("creates loan for active KYC customer")
        void applyForLoan_happyPath() {
            LoanApplicationRequest req = new LoanApplicationRequest(
                customerId, productId, accountId,
                new BigDecimal("10000.00"), 12, "Need funds"
            );

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(loanProduct));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

            Loan saved = buildLoan(LoanStatus.SUBMITTED);
            when(loanRepository.save(any())).thenReturn(saved);

            LoanResponse resp = loanService.applyForLoan(req);
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("LOAN"), any(), eq("APPLIED"), isNull(), any());
        }

        @Test
        @DisplayName("throws when customer not found")
        void applyForLoan_customerNotFound_throws() {
            LoanApplicationRequest req = new LoanApplicationRequest(
                customerId, productId, accountId,
                new BigDecimal("10000.00"), 12, null
            );
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.applyForLoan(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("throws when customer KYC is not ACTIVE")
        void applyForLoan_kycNotActive_throws() {
            activeCustomer.setKycStatus(KycStatus.PENDING_KYC);
            LoanApplicationRequest req = new LoanApplicationRequest(
                customerId, productId, accountId,
                new BigDecimal("10000.00"), 12, null
            );
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> loanService.applyForLoan(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("throws when principal exceeds product max")
        void applyForLoan_principalTooHigh_throws() {
            LoanApplicationRequest req = new LoanApplicationRequest(
                customerId, productId, accountId,
                new BigDecimal("999999.00"), 12, null
            );
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(loanProduct));
            lenient().when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> loanService.applyForLoan(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("throws when term exceeds product max")
        void applyForLoan_termTooLong_throws() {
            LoanApplicationRequest req = new LoanApplicationRequest(
                customerId, productId, accountId,
                new BigDecimal("10000.00"), 120, null
            );
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(loanProductRepository.findById(productId)).thenReturn(Optional.of(loanProduct));
            lenient().when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> loanService.applyForLoan(req))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("approveLoan")
    class ApproveLoan {

        @Test
        @DisplayName("approves loan in SUBMITTED state")
        void approveLoan_fromSubmitted_succeeds() {
            activeLoan.setStatus(LoanStatus.SUBMITTED);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            LoanResponse resp = loanService.approveLoan(loanId, "manager1");
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("LOAN"), any(), eq("APPROVED"), any(), any());
        }

        @Test
        @DisplayName("throws when loan is already ACTIVE")
        void approveLoan_alreadyActive_throws() {
            activeLoan.setStatus(LoanStatus.ACTIVE);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.approveLoan(loanId, "mgr"))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("throws when loan not found")
        void approveLoan_notFound_throws() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.approveLoan(loanId, "mgr"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("disburseLoan")
    class DisburseLoan {

        @Test
        @DisplayName("disburses approved loan: credits the account and posts the journal")
        void disburseLoan_approved_credits() {
            activeLoan.setStatus(LoanStatus.APPROVED);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(scheduleEngine.generateAnnuitySchedule(any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of());
            when(loanRepository.save(any())).thenReturn(activeLoan);

            LoanResponse resp = loanService.disburseLoan(loanId);

            assertThat(resp).isNotNull();
            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
            verify(loanGlPosting).postDisbursement(activeLoan, new BigDecimal("10000.00"), BigDecimal.ZERO, LocalDate.now());
            assertThat(activeLoan.getInterestAccruedThrough()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("origination fees (product + disbursement charges) are deducted and deferred")
        void disburseLoan_withFees_deductsAndDefers() {
            activeLoan.setStatus(LoanStatus.APPROVED);
            loanProduct.setOriginationFee(new BigDecimal("150.00"));
            LoanCharge processing = new LoanCharge();
            processing.setName("Processing");
            processing.setChargeTimeType(com.cba.charge.ChargeDefinition.ChargeTimeType.DISBURSEMENT);
            processing.setAmount(new BigDecimal("50.00"));
            processing.setAmountOutstanding(new BigDecimal("50.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loanId)).thenReturn(List.of(processing));
            when(scheduleEngine.generateAnnuitySchedule(any(), any(), any(), anyInt(), any()))
                .thenReturn(new ArrayList<>(List.of(
                    installment(1, LocalDate.now().plusMonths(1), "5000.00", "100.00"),
                    installment(2, LocalDate.now().plusMonths(2), "5000.00", "50.00"))));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            loanService.disburseLoan(loanId);

            verify(loanGlPosting).postDisbursement(activeLoan, new BigDecimal("10000.00"), new BigDecimal("200.00"),
                LocalDate.now());
            assertThat(activeLoan.getDeferredFee()).isEqualByComparingTo("200.00");
            assertThat(activeLoan.getDeferredFeeRemaining()).isEqualByComparingTo("200.00");
            assertThat(processing.isPaid()).isTrue();
            // The fee is spread over the periods by the effective interest method, summing exactly.
            assertThat(activeLoan.getRepaymentSchedule().stream().map(LoanRepaymentSchedule::getFeeAmortisation)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("200.00");
            verify(loanChargeRepository, times(2)).save(any(LoanCharge.class)); // processing + product fee record
        }

        @Test
        @DisplayName("fees at least the principal are rejected before any money moves")
        void disburseLoan_feeTooLarge_rejected() {
            activeLoan.setStatus(LoanStatus.APPROVED);
            loanProduct.setOriginationFee(new BigDecimal("10000.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.disburseLoan(loanId))
                .isInstanceOf(CbaException.class).hasMessageContaining("not less than");
            verifyNoInteractions(loanGlPosting);
        }

        @Test
        @DisplayName("throws when loan is not APPROVED")
        void disburseLoan_notApproved_throws() {
            activeLoan.setStatus(LoanStatus.SUBMITTED);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.disburseLoan(loanId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("throws when linked account is not ACTIVE")
        void disburseLoan_accountInactive_throws() {
            activeLoan.setStatus(LoanStatus.APPROVED);
            activeAccount.setStatus(AccountStatus.DORMANT);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanGlPosting.postDisbursement(any(), any(), any(), any()))
                .thenThrow(CbaException.badRequest("ACCOUNT_NOT_ACTIVE", "dormant"));

            assertThatThrownBy(() -> loanService.disburseLoan(loanId))
                .isInstanceOf(CbaException.class);
            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.APPROVED);
            verifyNoInteractions(scheduleEngine);
        }
    }

    @Nested
    @DisplayName("makeRepayment")
    class Repayment {

        @BeforeEach
        void schedule() {
            activeLoan.setDisbursementDate(LocalDate.now().minusMonths(2));
            activeLoan.setOutstandingBalance(new BigDecimal("1700.00"));
            activeLoan.getRepaymentSchedule().add(installment(1, LocalDate.now().minusDays(5), "800.00", "100.00"));
            activeLoan.getRepaymentSchedule().add(installment(2, LocalDate.now().plusDays(25), "900.00", "50.00"));
            lenient().when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            lenient().when(loanRepository.save(any())).thenReturn(activeLoan);
        }

        @Test
        @DisplayName("allocates interest then principal per instalment and posts the split")
        void repayment_allocates_andPosts() {
            LoanRepaymentResponse resp = loanService.makeRepayment(loanId,
                    new LoanRepaymentRequest(new BigDecimal("1000.00"), null, null, null, null));

            // Instalment 1: 100 interest + 800 principal; instalment 2: 50 interest + 50 principal.
            assertThat(resp.interestPortion()).isEqualByComparingTo("150.00");
            assertThat(resp.principalPortion()).isEqualByComparingTo("850.00");
            assertThat(resp.outstandingBalanceAfter()).isEqualByComparingTo("850.00");
            assertThat(resp.paymentMethod()).isEqualTo("ACCOUNT");
            assertThat(activeLoan.getRepaymentSchedule().get(0).getStatus())
                    .isEqualTo(LoanRepaymentSchedule.InstallmentStatus.PAID);
            assertThat(activeLoan.getRepaymentSchedule().get(1).getStatus())
                    .isEqualTo(LoanRepaymentSchedule.InstallmentStatus.PARTIALLY_PAID);
            verify(loanGlPosting).postRepayment(eq(activeLoan),
                    eq(new LoanPaymentSource(LoanPaymentSource.Method.ACCOUNT, null, null)),
                    eq(new LoanGlPosting.Allocation(new BigDecimal("850.00"), new BigDecimal("150.00"),
                            BigDecimal.ZERO.setScale(2))),
                    eq(LocalDate.now()), eq("RPMT"), anyString());
        }

        @Test
        @DisplayName("paying the whole schedule closes the loan")
        void repayment_full_closesLoan() {
            loanService.makeRepayment(loanId, new LoanRepaymentRequest(new BigDecimal("1850.00"), null, null, null, null));

            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.CLOSED_OBLIGATIONS_MET);
            assertThat(activeLoan.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(loanImpairmentService).release(activeLoan, LocalDate.now());
        }

        @Test
        @DisplayName("more than the schedule owes is rejected, nothing collected")
        void repayment_overpayment_rejected() {
            assertThatThrownBy(() -> loanService.makeRepayment(loanId,
                    new LoanRepaymentRequest(new BigDecimal("1850.01"), null, null, null, null)))
                    .isInstanceOf(CbaException.class).hasMessageContaining("exceeds");
            verifyNoInteractions(loanGlPosting);
        }

        @Test
        @DisplayName("cash needs a teller session")
        void repayment_cashWithoutSession_rejected() {
            assertThatThrownBy(() -> loanService.makeRepayment(loanId,
                    new LoanRepaymentRequest(new BigDecimal("100.00"), null, "CASH", null, null)))
                    .isInstanceOf(CbaException.class).hasMessageContaining("teller session");
            verifyNoInteractions(loanGlPosting);
        }

        @Test
        @DisplayName("a future payment date is rejected")
        void repayment_futureDate_rejected() {
            assertThatThrownBy(() -> loanService.makeRepayment(loanId,
                    new LoanRepaymentRequest(new BigDecimal("100.00"), LocalDate.now().plusDays(1), null, null, null)))
                    .isInstanceOf(CbaException.class).hasMessageContaining("future");
        }
    }

    @Nested
    @DisplayName("getLoan / list")
    class Reads {

        @Test
        @DisplayName("returns loan response when found")
        void getLoan_found() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            LoanResponse resp = loanService.getLoan(loanId);
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("throws 404 when loan not found")
        void getLoan_notFound_throws() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> loanService.getLoan(loanId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("listLoans returns page")
        void listLoans_returnsPage() {
            Page<Loan> page = new PageImpl<>(List.of(activeLoan));
            when(loanRepository.findAll(any(Pageable.class))).thenReturn(page);
            Page<LoanResponse> result = loanService.listLoans(Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getCustomerLoans returns page for customer")
        void getCustomerLoans_returnsPage() {
            Page<Loan> page = new PageImpl<>(List.of(activeLoan));
            when(loanRepository.findByCustomerId(eq(customerId), any(Pageable.class))).thenReturn(page);
            Page<LoanResponse> result = loanService.getCustomerLoans(customerId, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getRepaymentSchedule returns empty list for new loan")
        void getRepaymentSchedule_empty() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            var schedule = loanService.getRepaymentSchedule(loanId);
            assertThat(schedule).isEmpty();
        }
    }

    /** A loan 200 days past due: principal 1000, interest 50 accrued (in suspense), a 20 penalty in suspense. */
    private LoanCharge impairedLoan() {
        activeLoan.setStatus(LoanStatus.IN_ARREARS);
        activeLoan.setDisbursementDate(LocalDate.now().minusDays(260));
        activeLoan.setOutstandingBalance(new BigDecimal("1000.00"));
        LoanRepaymentSchedule i = installment(1, LocalDate.now().minusDays(200), "1000.00", "50.00");
        i.setInterestAccrued(new BigDecimal("50.00"));
        activeLoan.getRepaymentSchedule().add(i);
        activeLoan.setDaysPastDue(200);
        activeLoan.setNonPerforming(true);
        activeLoan.setInterestInSuspense(new BigDecimal("50.00"));
        activeLoan.setDeferredFeeRemaining(new BigDecimal("10.00"));
        activeLoan.setLossAllowance(new BigDecimal("400.00"));
        LoanCharge penalty = new LoanCharge();
        penalty.setName("Late fee");
        penalty.setPenalty(true);
        penalty.setAmount(new BigDecimal("20.00"));
        penalty.setAmountOutstanding(new BigDecimal("20.00"));
        penalty.setIncomeRecognizedOn(LocalDate.now().minusDays(150));
        penalty.setSuspended(true);
        return penalty;
    }

    private void stage(int stage) {
        when(loanImpairmentService.remeasure(eq(activeLoan), any())).thenReturn(new LoanImpairmentService.Measurement(
                stage, new BigDecimal("990"), new BigDecimal("100"), new BigDecimal("45"),
                new BigDecimal("400.00"), new BigDecimal("1000.00")));
    }

    private void cbnRule(boolean on) {
        GlobalConfiguration flag = new GlobalConfiguration();
        flag.setName(LoanService.CBN_WRITE_OFF_RULES);
        flag.setEnabled(true);
        flag.setBooleanValue(on);
        when(globalConfigRepository.findByName(LoanService.CBN_WRITE_OFF_RULES)).thenReturn(Optional.of(flag));
    }

    @Nested
    @DisplayName("writeOffLoan")
    class WriteOff {

        @Test
        @DisplayName("derecognises the gross carrying amount against the allowance (IFRS 9 §5.4.4)")
        void writeOff_postsAgainstAllowance() {
            LoanCharge penalty = impairedLoan();
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);
            when(loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loanId)).thenReturn(List.of(penalty));
            stage(3);
            cbnRule(false);
            when(loanGlPosting.postWriteOff(any(), any(), any())).thenReturn("GL-WOFF");

            LoanResponse resp = loanService.writeOffLoan(loanId,
                    new WriteOffRequest(LocalDate.now(), "uncollectable", " BRD/2026/14 "));

            verify(loanAccrualService).bringCurrent(activeLoan, LocalDate.now());
            // Gross: 1000 principal + 50 interest + 20 penalty − 10 deferred fee − 70 suspense = 990.
            LoanGlPosting.WriteOffAmounts expected = new LoanGlPosting.WriteOffAmounts(
                    new BigDecimal("1000.00"), new BigDecimal("50.00"), new BigDecimal("20.00"),
                    new BigDecimal("10.00"), new BigDecimal("70.00"), new BigDecimal("400.00"));
            verify(loanGlPosting).postWriteOff(activeLoan, expected, LocalDate.now());
            assertThat(expected.gross()).isEqualByComparingTo("990.00");
            assertThat(resp.status()).isEqualTo(LoanStatus.WRITTEN_OFF);
            assertThat(activeLoan.getWrittenOffAmount()).isEqualByComparingTo("990.00");
            assertThat(activeLoan.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(activeLoan.getLossAllowance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(activeLoan.getInterestInSuspense()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(activeLoan.getDeferredFeeRemaining()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(activeLoan.getWriteOffApprovalReference()).isEqualTo("BRD/2026/14");
            assertThat(activeLoan.getWriteOffTransactionId()).isEqualTo("GL-WOFF");
            assertThat(activeLoan.getWriteOffSnapshot()).containsEntry("principal", new BigDecimal("1000.00"))
                    .containsEntry("lossAllowance", new BigDecimal("400.00"));
            // The audit records the real previous status, not a hard-coded ACTIVE.
            verify(auditLogService).log(eq("LOAN"), eq(loanId.toString()), eq("WRITE_OFF"), eq("IN_ARREARS"), any());
        }

        @Test
        @DisplayName("rejects a write-off without a board-approval reference, before touching the GL")
        void writeOff_withoutApproval_throws() {
            impairedLoan();
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(LocalDate.now(), "bad debt")))
                .isInstanceOf(CbaException.class).hasMessageContaining("approval reference");
            verifyNoInteractions(loanGlPosting, loanAccrualService, loanImpairmentService);
        }

        @Test
        @DisplayName("only a credit-impaired (stage 3) loan can be written off")
        void writeOff_notStage3_throws() {
            impairedLoan();
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            stage(2);

            assertThatThrownBy(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(null, "bad debt", "BRD-1")))
                .isInstanceOf(CbaException.class).hasMessageContaining("stage 2");
            verify(loanGlPosting, never()).postWriteOff(any(), any(), any());
        }

        @Test
        @DisplayName("CBN rule on: not fully provided for a year → rejected")
        void writeOff_cbnRuleNotMet_throws() {
            impairedLoan();
            activeLoan.setFullyProvidedSince(LocalDate.now().minusMonths(6));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            stage(3);
            cbnRule(true);

            assertThatThrownBy(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(null, "bad debt", "BRD-1")))
                .isInstanceOf(CbaException.class).hasMessageContaining("fully provided since");
            verify(loanGlPosting, never()).postWriteOff(any(), any(), any());
        }

        @Test
        @DisplayName("CBN rule on: fully provided for over a year → written off")
        void writeOff_cbnRuleMet_succeeds() {
            LoanCharge penalty = impairedLoan();
            activeLoan.setFullyProvidedSince(LocalDate.now().minusYears(1).minusDays(1));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);
            when(loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loanId)).thenReturn(List.of(penalty));
            stage(3);
            cbnRule(true);

            loanService.writeOffLoan(loanId, new WriteOffRequest(null, "bad debt", "BRD-1"));
            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.WRITTEN_OFF);
        }

        @Test
        @DisplayName("throws when loan already written off")
        void writeOff_alreadyWrittenOff_throws() {
            activeLoan.setStatus(LoanStatus.WRITTEN_OFF);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(LocalDate.now(), "bad debt", "BRD-1")))
                .isInstanceOf(CbaException.class);
        }
    }

    private void writtenOff() {
        activeLoan.setStatus(LoanStatus.WRITTEN_OFF);
        activeLoan.setDisbursementDate(LocalDate.now().minusDays(260));
        activeLoan.setOutstandingBalance(BigDecimal.ZERO);
        activeLoan.getRepaymentSchedule().add(installment(1, LocalDate.now().minusDays(200), "1000.00", "50.00"));
        activeLoan.setWrittenOffOn(LocalDate.now().minusDays(10));
        activeLoan.setWriteOffTransactionId("GL-WOFF");
        activeLoan.setWriteOffSnapshot(new java.util.HashMap<>(java.util.Map.of(
                "principal", new BigDecimal("1000.00"), "interestInSuspense", new BigDecimal("50.00"),
                "deferredFeeRemaining", new BigDecimal("10.00"), "lossAllowance", new BigDecimal("400.00"),
                "prudentialProvision", new BigDecimal("1000.00"), "eclStage", new BigDecimal("3"))));
    }

    @Nested
    @DisplayName("undoWriteOff")
    class UndoWriteOff {

        @Test
        @DisplayName("reverses the write-off journal on the write-off date and restores the balances")
        void undoWriteOff_reversesAndRestores() {
            writtenOff();
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            loanService.undoWriteOff(loanId);

            verify(loanGlPosting).postWriteOffReversal(activeLoan, "GL-WOFF", LocalDate.now().minusDays(10));
            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.IN_ARREARS);
            assertThat(activeLoan.getOutstandingBalance()).isEqualByComparingTo("1000.00");
            assertThat(activeLoan.getLossAllowance()).isEqualByComparingTo("400.00");
            assertThat(activeLoan.getInterestInSuspense()).isEqualByComparingTo("50.00");
            assertThat(activeLoan.getEclStage()).isEqualTo(3);
            assertThat(activeLoan.getWrittenOffOn()).isNull();
            assertThat(activeLoan.getWriteOffSnapshot()).isNull();
        }

        @Test
        @DisplayName("refused once cash has been recovered")
        void undoWriteOff_afterRecovery_throws() {
            writtenOff();
            activeLoan.setRecoveredAmount(new BigDecimal("100.00"));
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.undoWriteOff(loanId))
                .isInstanceOf(CbaException.class).hasMessageContaining("recovered");
            verifyNoInteractions(loanGlPosting);
        }

        @Test
        @DisplayName("a write-off from before GL posting has no journal: principal comes back from the schedule")
        void undoWriteOff_legacy_restoresFromSchedule() {
            writtenOff();
            activeLoan.setWriteOffTransactionId(null);
            activeLoan.setWriteOffSnapshot(null);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            loanService.undoWriteOff(loanId);

            verifyNoInteractions(loanGlPosting);
            assertThat(activeLoan.getOutstandingBalance()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("throws when loan is not written off")
        void undoWriteOff_notWrittenOff_throws() {
            activeLoan.setStatus(LoanStatus.ACTIVE);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.undoWriteOff(loanId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Test
    @DisplayName("ownership: only the borrower's own Keycloak subject passes; anything else is 404")
    void requireOwnedBy() {
        activeCustomer.setKeycloakId("kc-owner");
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

        assertThatCode(() -> loanService.requireOwnedBy(loanId, "kc-owner")).doesNotThrowAnyException();
        assertThatThrownBy(() -> loanService.requireOwnedBy(loanId, "kc-someone-else"))
            .isInstanceOf(CbaException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> loanService.requireOwnedBy(loanId, null))
            .isInstanceOf(CbaException.class).hasMessageContaining("not found");

        activeCustomer.setKeycloakId(null); // a borrower with no login owns nothing a caller can claim
        assertThatThrownBy(() -> loanService.requireOwnedBy(loanId, "kc-owner"))
            .isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("customer view drops the bank's internal credit-risk and write-off approval figures")
    void forCustomer_redactsInternalFields() {
        writtenOff();
        activeLoan.setWriteOffReason("insolvent");
        activeLoan.setWriteOffApprovalReference("BRD/2026/14");
        activeLoan.setWrittenOffAmount(new BigDecimal("990.00"));
        activeLoan.setLossAllowance(new BigDecimal("400.00"));
        activeLoan.setPrudentialProvision(new BigDecimal("1000.00"));
        activeLoan.setEclStage(3);
        activeLoan.setNonPerforming(true);
        activeLoan.setInterestInSuspense(new BigDecimal("50.00"));
        activeLoan.setRecoveredAmount(new BigDecimal("10.00"));

        LoanResponse staff = loanService.toResponse(activeLoan);
        LoanResponse customer = staff.forCustomer();

        assertThat(staff.writeOffApprovalReference()).isEqualTo("BRD/2026/14");
        assertThat(customer.writeOffApprovalReference()).isNull();
        assertThat(customer.writeOffReason()).isNull();
        assertThat(customer.writtenOffAmount()).isNull();
        assertThat(customer.lossAllowance()).isNull();
        assertThat(customer.prudentialProvision()).isNull();
        assertThat(customer.eclStage()).isNull();
        assertThat(customer.interestInSuspense()).isNull();
        assertThat(customer.nonPerforming()).isFalse();
        assertThat(customer.status()).isEqualTo(LoanStatus.WRITTEN_OFF);
        assertThat(customer.recoveredAmount()).isEqualByComparingTo("10.00");
    }

    @Nested
    @DisplayName("recovery after write-off")
    class Recovery {

        @Test
        @DisplayName("cash on a written-off loan is an impairment gain; the schedule keeps the legal debt")
        void recovery_postsGain() {
            writtenOff();
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            LoanRepaymentResponse resp = loanService.makeRepayment(loanId,
                    new LoanRepaymentRequest(new BigDecimal("300.00"), null, null, null, null));

            verify(loanGlPosting).postRecovery(eq(activeLoan), any(), eq(new BigDecimal("300.00")), eq(LocalDate.now()));
            verify(loanGlPosting, never()).postRepayment(any(), any(), any(), any(), any(), any());
            verifyNoInteractions(loanAccrualService);
            assertThat(activeLoan.getRecoveredAmount()).isEqualByComparingTo("300.00");
            assertThat(resp.interestPortion()).isEqualByComparingTo("50.00");
            assertThat(resp.principalPortion()).isEqualByComparingTo("250.00");
            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.WRITTEN_OFF);
        }

        @Test
        @DisplayName("more than the borrower still owes is rejected")
        void recovery_aboveDebt_throws() {
            writtenOff();
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.makeRepayment(loanId,
                    new LoanRepaymentRequest(new BigDecimal("1050.01"), null, null, null, null)))
                .isInstanceOf(CbaException.class).hasMessageContaining("exceeds");
            verifyNoInteractions(loanGlPosting);
        }
    }

    @Nested
    @DisplayName("waiveInterest")
    class WaiveInterest {

        @Test
        @DisplayName("waives interest on active loan")
        void waiveInterest_active_succeeds() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            WaiveInterestRequest req = new WaiveInterestRequest("goodwill");
            LoanResponse resp = loanService.waiveInterest(loanId, req);
            assertThat(resp).isNotNull();
        }
    }

    @Nested
    @DisplayName("forecloseLoan")
    class Foreclose {

        @Test
        @DisplayName("collects the quote: principal, interest already due, recognised charges")
        void foreclose_active_collectsQuote() {
            activeLoan.setDisbursementDate(LocalDate.now().minusMonths(2));
            activeLoan.setOutstandingBalance(new BigDecimal("1700.00"));
            activeLoan.getRepaymentSchedule().add(installment(1, LocalDate.now().minusDays(5), "800.00", "100.00"));
            activeLoan.getRepaymentSchedule().add(installment(2, LocalDate.now().plusDays(25), "900.00", "50.00"));
            LoanCharge penalty = new LoanCharge();
            penalty.setName("Late fee");
            penalty.setAmount(new BigDecimal("20.00"));
            penalty.setAmountOutstanding(new BigDecimal("20.00"));
            penalty.setIncomeRecognizedOn(LocalDate.now());
            LoanCharge futureFee = new LoanCharge();
            futureFee.setName("Statement fee");
            futureFee.setAmount(new BigDecimal("5.00"));
            futureFee.setAmountOutstanding(new BigDecimal("5.00"));

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);
            when(loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loanId)).thenReturn(List.of(penalty, futureFee));

            loanService.forecloseLoan(loanId, new ForecloseRequest(LocalDate.now(), "early settlement"));

            verify(loanGlPosting).postRepayment(eq(activeLoan), any(),
                    // Interest: 100.00 overdue + 5 of 30 days of the current period's 50.00 (earned, accrued).
                    eq(new LoanGlPosting.Allocation(new BigDecimal("1700.00"), new BigDecimal("108.3333"),
                            new BigDecimal("20.00"))),
                    eq(LocalDate.now()), eq("FCLS"), anyString());
            verify(loanAccrualService).accrueThrough(activeLoan, LocalDate.now());
            verify(loanAccrualService).closeOut(activeLoan, LocalDate.now());
            verify(loanImpairmentService).release(activeLoan, LocalDate.now());
            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.FORECLOSED);
            assertThat(activeLoan.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            // Interest on the instalment not yet due is cancelled, never collected.
            assertThat(activeLoan.getRepaymentSchedule().get(1).getInterestDue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(penalty.isPaid()).isTrue();
            assertThat(futureFee.isWaived()).isTrue();
        }

        @Test
        @DisplayName("throws when loan is not active")
        void foreclose_notActive_throws() {
            activeLoan.setStatus(LoanStatus.SUBMITTED);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.forecloseLoan(loanId, new ForecloseRequest(LocalDate.now(), "legal")))
                .isInstanceOf(CbaException.class);
        }
    }

    private LoanRepaymentSchedule installment(int no, LocalDate due, String principal, String interest) {
        LoanRepaymentSchedule s = new LoanRepaymentSchedule();
        s.setLoan(activeLoan);
        s.setInstallmentNo(no);
        s.setDueDate(due);
        s.setPrincipalDue(new BigDecimal(principal));
        s.setInterestDue(new BigDecimal(interest));
        s.setFeesDue(BigDecimal.ZERO.setScale(2));
        s.setTotalDue(new BigDecimal(principal).add(new BigDecimal(interest)));
        return s;
    }

    private Loan buildLoan(LoanStatus status) {
        Loan l = new Loan();
        l.setId(UUID.randomUUID());
        l.setLoanAccountNumber("LN-001-TEST");
        l.setCustomer(activeCustomer);
        l.setProduct(loanProduct);
        l.setLinkedAccount(activeAccount);
        l.setPrincipalAmount(new BigDecimal("10000.00"));
        l.setApprovedAmount(new BigDecimal("10000.00"));
        l.setInterestRate(new BigDecimal("12.00"));
        l.setTermMonths(12);
        l.setStatus(status);
        l.setOutstandingBalance(BigDecimal.ZERO);
        l.setRepaymentSchedule(new ArrayList<>());
        return l;
    }
}

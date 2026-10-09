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
            verify(loanGlPosting).postDisbursement(activeLoan, new BigDecimal("10000.00"), LocalDate.now());
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
            when(loanGlPosting.postDisbursement(any(), any(), any()))
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

    @Nested
    @DisplayName("writeOffLoan")
    class WriteOff {

        @Test
        @DisplayName("write-off active loan")
        void writeOff_active_succeeds() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            WriteOffRequest req = new WriteOffRequest(LocalDate.now(), "bad debt");
            LoanResponse resp = loanService.writeOffLoan(loanId, req);
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("throws when loan already written off")
        void writeOff_alreadyWrittenOff_throws() {
            activeLoan.setStatus(LoanStatus.WRITTEN_OFF);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() -> loanService.writeOffLoan(loanId, new WriteOffRequest(LocalDate.now(), "bad debt")))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("undoWriteOff")
    class UndoWriteOff {

        @Test
        @DisplayName("undo write-off restores loan to ACTIVE")
        void undoWriteOff_succeeds() {
            activeLoan.setStatus(LoanStatus.WRITTEN_OFF);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            LoanResponse resp = loanService.undoWriteOff(loanId);
            assertThat(resp).isNotNull();
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
                    eq(new LoanGlPosting.Allocation(new BigDecimal("1700.00"), new BigDecimal("100.00"),
                            new BigDecimal("20.00"))),
                    eq(LocalDate.now()), eq("FCLS"), anyString());
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

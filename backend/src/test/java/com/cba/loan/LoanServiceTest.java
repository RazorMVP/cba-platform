package com.cba.loan;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import com.cba.account.AccountType;
import com.cba.account.Transaction;
import com.cba.account.TransactionRepository;
import com.cba.audit.AuditLogService;
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
        @DisplayName("disburses approved loan and credits account")
        void disburseLoan_approved_credits() {
            activeLoan.setStatus(LoanStatus.APPROVED);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);
            when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));
            when(scheduleEngine.generateAnnuitySchedule(any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of());
            when(loanRepository.save(any())).thenReturn(activeLoan);

            LoanResponse resp = loanService.disburseLoan(loanId);
            assertThat(resp).isNotNull();
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
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> loanService.disburseLoan(loanId))
                .isInstanceOf(CbaException.class);
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
        @DisplayName("forecloses active loan")
        void foreclose_active_succeeds() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(activeLoan));
            when(loanRepository.save(any())).thenReturn(activeLoan);

            ForecloseRequest req = new ForecloseRequest(LocalDate.now(), "legal action");
            LoanResponse resp = loanService.forecloseLoan(loanId, req);
            assertThat(resp).isNotNull();
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

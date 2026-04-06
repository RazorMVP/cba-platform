package com.cba.loan;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import com.cba.account.Transaction;
import com.cba.account.TransactionRepository;
import com.cba.account.TransactionType;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.customer.KycStatus;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanResponse;
import com.cba.loan.dto.RepaymentScheduleResponse;
import com.cba.notification.LoanEvent;
import com.cba.product.LoanProduct;
import com.cba.product.LoanProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private final LoanRepository loanRepository;
    private final CustomerRepository customerRepository;
    private final LoanProductRepository loanProductRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RepaymentScheduleEngine scheduleEngine;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    private static final String LOAN_TYPE = "LN";

    @Transactional
    public LoanResponse applyForLoan(LoanApplicationRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
            .orElseThrow(() -> CbaException.notFound("Customer", request.customerId()));

        if (customer.getKycStatus() != KycStatus.ACTIVE) {
            throw CbaException.badRequest("CUSTOMER_NOT_KYC_ACTIVE",
                "Customer must have active KYC to apply for a loan");
        }

        LoanProduct product = loanProductRepository.findById(request.productId())
            .orElseThrow(() -> CbaException.notFound("LoanProduct", request.productId()));

        validateLoanParameters(request, product);

        Account linkedAccount = accountRepository.findById(request.linkedAccountId())
            .orElseThrow(() -> CbaException.notFound("Account", request.linkedAccountId()));

        Loan loan = new Loan();
        loan.setLoanAccountNumber(generateLoanNumber());
        loan.setCustomer(customer);
        loan.setProduct(product);
        loan.setLinkedAccount(linkedAccount);
        loan.setPrincipalAmount(request.principalAmount());
        loan.setInterestRate(product.getDefaultInterestRate());
        loan.setTermMonths(request.termMonths());
        loan.setNotes(request.notes());

        Loan saved = loanRepository.save(loan);

        auditLogService.log("LOAN", saved.getId().toString(), "APPLIED", null, request);
        eventPublisher.publishEvent(new LoanEvent(this, saved.getId(), customer.getId(), LoanEvent.Type.APPLIED));

        log.info("Loan application submitted: {}", saved.getLoanAccountNumber());
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse approveLoan(UUID id, String approvedBy) {
        Loan loan = findById(id);

        if (loan.getStatus() != LoanStatus.SUBMITTED && loan.getStatus() != LoanStatus.UNDER_REVIEW) {
            throw CbaException.badRequest("INVALID_LOAN_STATE",
                "Loan can only be approved from SUBMITTED or UNDER_REVIEW state");
        }

        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovedAmount(loan.getPrincipalAmount());
        loan.setApprovalDate(LocalDate.now());
        loan.setApprovedBy(approvedBy);

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", id.toString(), "APPROVED", LoanStatus.SUBMITTED.name(), LoanStatus.APPROVED.name());
        eventPublisher.publishEvent(new LoanEvent(this, id, loan.getCustomer().getId(), LoanEvent.Type.APPROVED));

        log.info("Loan approved: {}", saved.getLoanAccountNumber());
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse disburseLoan(UUID id) {
        Loan loan = findById(id);

        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw CbaException.badRequest("INVALID_LOAN_STATE", "Loan must be APPROVED before disbursement");
        }

        Account account = accountRepository.findByIdWithLock(loan.getLinkedAccount().getId())
            .orElseThrow(() -> CbaException.notFound("Account", loan.getLinkedAccount().getId()));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE", "Disbursement account is not active");
        }

        // Credit loan amount to linked account
        BigDecimal amount = loan.getApprovedAmount();
        account.credit(amount);
        accountRepository.save(account);

        // Create transaction record
        Transaction tx = Transaction.of(account, TransactionType.LOAN_DISBURSEMENT, amount,
            account.getBalance(), "Loan disbursement: " + loan.getLoanAccountNumber(),
            "DISB-" + loan.getLoanAccountNumber(), "system");
        transactionRepository.save(tx);

        // Build repayment schedule
        LocalDate firstDueDate = LocalDate.now().plusMonths(1);
        List<LoanRepaymentSchedule> schedule = scheduleEngine.generateAnnuitySchedule(
            loan, amount, loan.getInterestRate(), loan.getTermMonths(), firstDueDate);

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setOutstandingBalance(amount);
        loan.setDisbursementDate(LocalDate.now());
        loan.setMaturityDate(firstDueDate.plusMonths(loan.getTermMonths() - 1L));
        loan.getRepaymentSchedule().clear();
        loan.getRepaymentSchedule().addAll(schedule);

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", id.toString(), "DISBURSED", LoanStatus.APPROVED.name(), LoanStatus.ACTIVE.name());
        eventPublisher.publishEvent(new LoanEvent(this, id, loan.getCustomer().getId(), LoanEvent.Type.DISBURSED));

        log.info("Loan disbursed: {} — amount={}", saved.getLoanAccountNumber(), amount);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoan(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> listLoans(Pageable pageable) {
        return loanRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getCustomerLoans(UUID customerId, Pageable pageable) {
        return loanRepository.findByCustomerId(customerId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<RepaymentScheduleResponse> getRepaymentSchedule(UUID loanId) {
        Loan loan = findById(loanId);
        return loan.getRepaymentSchedule().stream().map(this::toScheduleResponse).toList();
    }

    private void validateLoanParameters(LoanApplicationRequest req, LoanProduct product) {
        if (req.principalAmount().compareTo(product.getMinPrincipal()) < 0 ||
            req.principalAmount().compareTo(product.getMaxPrincipal()) > 0) {
            throw CbaException.badRequest("LOAN_AMOUNT_OUT_OF_RANGE",
                "Principal must be between " + product.getMinPrincipal() + " and " + product.getMaxPrincipal());
        }
        if (req.termMonths() < product.getMinTermMonths() || req.termMonths() > product.getMaxTermMonths()) {
            throw CbaException.badRequest("LOAN_TERM_OUT_OF_RANGE",
                "Term must be between " + product.getMinTermMonths() + " and " + product.getMaxTermMonths() + " months");
        }
    }

    private String generateLoanNumber() {
        return "001-LN-" + String.format("%07d", System.currentTimeMillis() % 10_000_000);
    }

    private Loan findById(UUID id) {
        return loanRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Loan", id));
    }

    LoanResponse toResponse(Loan l) {
        String customerName = l.getCustomer().getFirstName() + " " + l.getCustomer().getLastName();
        return new LoanResponse(
            l.getId(), l.getLoanAccountNumber(),
            l.getCustomer().getId(), customerName,
            l.getProduct().getName(),
            l.getPrincipalAmount(), l.getApprovedAmount(), l.getOutstandingBalance(),
            l.getInterestRate(), l.getTermMonths(), l.getStatus(),
            l.getApplicationDate(), l.getApprovalDate(),
            l.getDisbursementDate(), l.getMaturityDate()
        );
    }

    RepaymentScheduleResponse toScheduleResponse(LoanRepaymentSchedule s) {
        return new RepaymentScheduleResponse(
            s.getId(), s.getInstallmentNo(), s.getDueDate(),
            s.getPrincipalDue(), s.getInterestDue(), s.getFeesDue(), s.getTotalDue(),
            s.getPrincipalPaid(), s.getInterestPaid(), s.getTotalPaid(),
            s.getStatus(), s.getPaidDate()
        );
    }
}

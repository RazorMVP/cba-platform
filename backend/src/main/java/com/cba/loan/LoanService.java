package com.cba.loan;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
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
import com.cba.loan.dto.RepaymentScheduleResponse;
import com.cba.loan.dto.ForecloseRequest;
import com.cba.loan.dto.ForeclosureQuote;
import com.cba.loan.dto.WaiveInterestRequest;
import com.cba.loan.dto.WriteOffRequest;
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
    private final RepaymentScheduleEngine scheduleEngine;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final LoanGlPosting loanGlPosting;
    private final LoanChargeRepository loanChargeRepository;

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

        // Credits the linked account (lock, status check, transaction record) and posts
        // DR loan portfolio / CR savings control in this transaction.
        BigDecimal amount = loan.getApprovedAmount();
        loanGlPosting.postDisbursement(loan, amount, LocalDate.now());

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

    @Transactional
    public LoanRepaymentResponse makeRepayment(UUID loanId, LoanRepaymentRequest request) {
        Loan loan = findById(loanId);

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.IN_ARREARS) {
            throw CbaException.badRequest("INVALID_LOAN_STATE",
                    "Loan must be ACTIVE or IN_ARREARS to accept repayments");
        }

        LocalDate date = paymentDate(loan, request.paymentDate());
        LoanPaymentSource source = LoanPaymentSource.of(
                request.paymentMethod(), request.sourceAccountId(), request.tellerSessionId());
        BigDecimal payment = request.amount();

        // Money the schedule does not owe has nowhere to go in the ledger: reject it
        // rather than take it from the customer and lose it.
        BigDecimal owed = scheduledOutstanding(loan);
        if (payment.compareTo(owed) > 0) {
            throw CbaException.badRequest("REPAYMENT_EXCEEDS_OUTSTANDING",
                    "Repayment " + payment + " exceeds the " + owed + " still owed on the schedule");
        }

        LoanGlPosting.Allocation allocation = allocate(loan, payment, date);
        loanGlPosting.postRepayment(loan, source, allocation, date, "RPMT",
                "Loan repayment " + loan.getLoanAccountNumber());

        loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(allocation.principal()));
        if (loan.getOutstandingBalance().signum() <= 0) {
            loan.setOutstandingBalance(BigDecimal.ZERO);
            loan.setStatus(LoanStatus.CLOSED_OBLIGATIONS_MET);
        }

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loanId.toString(), "REPAYMENT", null,
                java.util.Map.of("amount", payment, "principalPortion", allocation.principal(),
                        "interestPortion", allocation.interest(), "feePortion", allocation.fees(),
                        "paymentMethod", source.method().name()));

        return new LoanRepaymentResponse(
                saved.getId(), saved.getLoanAccountNumber(),
                payment, allocation.principal(), allocation.interest(), allocation.fees(),
                saved.getOutstandingBalance(), date,
                source.method().name(), request.referenceNumber());
    }

    /**
     * Applies {@code payment} to unpaid instalments in due-date order, each one's fees,
     * then interest, then principal (Fineract's default strategy), and returns the split.
     */
    private LoanGlPosting.Allocation allocate(Loan loan, BigDecimal payment, LocalDate date) {
        BigDecimal remaining = payment;
        BigDecimal fees = BigDecimal.ZERO, interest = BigDecimal.ZERO, principal = BigDecimal.ZERO;
        for (LoanRepaymentSchedule i : loan.getRepaymentSchedule()) {
            if (remaining.signum() == 0) break;
            if (i.getStatus() == LoanRepaymentSchedule.InstallmentStatus.PAID) continue;

            BigDecimal fee = unpaidFees(i).min(remaining);
            remaining = remaining.subtract(fee);
            BigDecimal intr = unpaidInterest(i).min(remaining);
            remaining = remaining.subtract(intr);
            BigDecimal prin = unpaidPrincipal(i).min(remaining);
            remaining = remaining.subtract(prin);

            i.setFeesPaid(i.getFeesPaid().add(fee));
            i.setInterestPaid(i.getInterestPaid().add(intr));
            i.setPrincipalPaid(i.getPrincipalPaid().add(prin));
            markPaidStatus(i, date);
            fees = fees.add(fee);
            interest = interest.add(intr);
            principal = principal.add(prin);
        }
        return new LoanGlPosting.Allocation(principal, interest, fees);
    }

    /** Settles the loan early for the foreclosure quote, collected from the borrower. */
    @Transactional(readOnly = true)
    public ForeclosureQuote getForeclosureQuote(UUID loanId, LocalDate date) {
        Loan loan = findById(loanId);
        requireOpen(loan, "be foreclosed");
        return quote(loan, date != null ? date : LocalDate.now());
    }

    private ForeclosureQuote quote(Loan loan, LocalDate date) {
        BigDecimal interest = BigDecimal.ZERO, fees = BigDecimal.ZERO;
        for (LoanRepaymentSchedule i : loan.getRepaymentSchedule()) {
            if (i.getStatus() == LoanRepaymentSchedule.InstallmentStatus.PAID || i.getDueDate().isAfter(date)) continue;
            interest = interest.add(unpaidInterest(i));
            fees = fees.add(unpaidFees(i));
        }
        BigDecimal charges = loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loan.getId()).stream()
                .filter(c -> c.getIncomeRecognizedOn() != null && !c.isPaid() && !c.isWaived())
                .map(LoanCharge::getAmountOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal principal = loan.getOutstandingBalance();
        return new ForeclosureQuote(loan.getId(), loan.getLoanAccountNumber(), date, LoanGlPosting.currency(loan),
                principal, interest, fees, charges, principal.add(interest).add(fees).add(charges));
    }

    @Transactional
    public LoanResponse rejectLoan(UUID loanId, String reason) {
        Loan loan = findById(loanId);
        if (loan.getStatus() != LoanStatus.SUBMITTED && loan.getStatus() != LoanStatus.UNDER_REVIEW
                && loan.getStatus() != LoanStatus.APPROVED) {
            throw CbaException.badRequest("INVALID_LOAN_STATE",
                    "Only SUBMITTED, UNDER_REVIEW or APPROVED loans can be rejected");
        }
        LoanStatus old = loan.getStatus();
        loan.setStatus(LoanStatus.REJECTED);
        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loanId.toString(), "REJECTED", old.name(),
                java.util.Map.of("status", LoanStatus.REJECTED.name(), "reason", reason));
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse writeOffLoan(UUID loanId, WriteOffRequest request) {
        Loan loan = findById(loanId);

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.IN_ARREARS) {
            throw CbaException.badRequest("INVALID_LOAN_STATE",
                    "Only ACTIVE or IN_ARREARS loans can be written off");
        }

        loan.setStatus(LoanStatus.WRITTEN_OFF);
        loan.setWrittenOffOn(request.writeOffDate() != null ? request.writeOffDate() : LocalDate.now());
        loan.setWriteOffReason(request.reason());
        loan.setOutstandingBalance(BigDecimal.ZERO);

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loanId.toString(), "WRITE_OFF", LoanStatus.ACTIVE.name(), LoanStatus.WRITTEN_OFF.name());
        log.info("Loan written off: {} — reason: {}", saved.getLoanAccountNumber(), request.reason());
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse undoWriteOff(UUID loanId) {
        Loan loan = findById(loanId);

        if (loan.getStatus() != LoanStatus.WRITTEN_OFF) {
            throw CbaException.badRequest("INVALID_LOAN_STATE", "Only WRITTEN_OFF loans can be un-written-off");
        }

        // Restore outstanding balance from unpaid schedule installments
        BigDecimal restored = loan.getRepaymentSchedule().stream()
            .filter(s -> s.getStatus() != LoanRepaymentSchedule.InstallmentStatus.PAID)
            .map(s -> s.getPrincipalDue().subtract(s.getPrincipalPaid() != null ? s.getPrincipalPaid() : BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        loan.setStatus(LoanStatus.IN_ARREARS);
        loan.setOutstandingBalance(restored);
        loan.setWrittenOffOn(null);
        loan.setWriteOffReason(null);

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loanId.toString(), "UNDO_WRITE_OFF", LoanStatus.WRITTEN_OFF.name(), LoanStatus.IN_ARREARS.name());
        log.info("Loan write-off reversed: {}", saved.getLoanAccountNumber());
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse waiveInterest(UUID loanId, WaiveInterestRequest request) {
        Loan loan = findById(loanId);

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.IN_ARREARS) {
            throw CbaException.badRequest("INVALID_LOAN_STATE",
                "Only ACTIVE or IN_ARREARS loans can have interest waived");
        }

        // Zero out unpaid interest on all pending installments
        BigDecimal totalWaived = BigDecimal.ZERO;
        for (LoanRepaymentSchedule installment : loan.getRepaymentSchedule()) {
            if (installment.getStatus() == LoanRepaymentSchedule.InstallmentStatus.PAID) continue;
            BigDecimal interestOutstanding = installment.getInterestDue()
                .subtract(installment.getInterestPaid() != null ? installment.getInterestPaid() : BigDecimal.ZERO);
            if (interestOutstanding.compareTo(BigDecimal.ZERO) > 0) {
                installment.setInterestPaid(installment.getInterestDue());
                totalWaived = totalWaived.add(interestOutstanding);
            }
        }

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loanId.toString(), "WAIVE_INTEREST", null,
            java.util.Map.of("totalWaived", totalWaived, "reason", request.reason()));
        log.info("Interest waived on loan {}: amount={}", saved.getLoanAccountNumber(), totalWaived);
        return toResponse(saved);
    }

    @Transactional
    public LoanResponse forecloseLoan(UUID loanId, ForecloseRequest request) {
        Loan loan = findById(loanId);

        requireOpen(loan, "be foreclosed");
        LocalDate date = paymentDate(loan, request.foreclosureDate());
        LoanPaymentSource source = LoanPaymentSource.of(
                request.paymentMethod(), request.sourceAccountId(), request.tellerSessionId());
        ForeclosureQuote q = quote(loan, date);

        // The borrower pays the quote; it clears the portfolio, the receivables for
        // interest and fees already due, and recognised charges.
        loanGlPosting.postRepayment(loan, source,
                new LoanGlPosting.Allocation(q.principal(), q.interest(), q.fees().add(q.charges())),
                date, "FCLS", "Loan foreclosure " + loan.getLoanAccountNumber());

        // Instalments due by the date are paid in full. Later ones keep their principal
        // (paid now) but lose their interest and fees: not yet earned, never recognised.
        BigDecimal cancelledInterest = BigDecimal.ZERO;
        for (LoanRepaymentSchedule i : loan.getRepaymentSchedule()) {
            if (i.getStatus() == LoanRepaymentSchedule.InstallmentStatus.PAID) continue;
            if (i.getDueDate().isAfter(date)) {
                cancelledInterest = cancelledInterest.add(unpaidInterest(i));
                i.setInterestDue(i.getInterestPaid());
                i.setFeesDue(i.getFeesPaid());
                i.setTotalDue(i.getPrincipalDue().add(i.getInterestDue()).add(i.getFeesDue()));
            } else {
                i.setInterestPaid(i.getInterestDue());
                i.setFeesPaid(i.getFeesDue());
            }
            i.setPrincipalPaid(i.getPrincipalDue());
            markPaidStatus(i, date);
        }
        // Recognised charges were just paid; charges not yet income are cancelled.
        for (LoanCharge c : loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loanId)) {
            if (c.isPaid() || c.isWaived() || c.getAmountOutstanding().signum() <= 0) continue;
            if (c.getIncomeRecognizedOn() != null) {
                c.setAmountPaid(c.getAmountPaid().add(c.getAmountOutstanding()));
                c.setPaid(true);
            } else {
                c.setAmountWaived(c.getAmountWaived().add(c.getAmountOutstanding()));
                c.setWaived(true);
            }
            c.setAmountOutstanding(BigDecimal.ZERO);
            loanChargeRepository.save(c);
        }

        LoanStatus old = loan.getStatus();
        loan.setStatus(LoanStatus.FORECLOSED);
        loan.setOutstandingBalance(BigDecimal.ZERO);

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loanId.toString(), "FORECLOSED", old.name(),
            java.util.Map.of("status", LoanStatus.FORECLOSED.name(), "amountPaid", q.total(),
                "cancelledFutureInterest", cancelledInterest, "reason", request.reason()));
        log.info("Loan foreclosed: {} — paid {}", saved.getLoanAccountNumber(), q.total());
        return toResponse(saved);
    }

    // ── Schedule helpers ─────────────────────────────────────────────────────

    private static BigDecimal unpaidFees(LoanRepaymentSchedule i) {
        return i.getFeesDue().subtract(i.getFeesPaid()).max(BigDecimal.ZERO);
    }

    private static BigDecimal unpaidInterest(LoanRepaymentSchedule i) {
        return i.getInterestDue().subtract(i.getInterestPaid()).max(BigDecimal.ZERO);
    }

    private static BigDecimal unpaidPrincipal(LoanRepaymentSchedule i) {
        return i.getPrincipalDue().subtract(i.getPrincipalPaid()).max(BigDecimal.ZERO);
    }

    /** Everything still owed on the schedule: fees, interest and principal. */
    private static BigDecimal scheduledOutstanding(Loan loan) {
        return loan.getRepaymentSchedule().stream()
                .filter(i -> i.getStatus() != LoanRepaymentSchedule.InstallmentStatus.PAID)
                .map(i -> unpaidFees(i).add(unpaidInterest(i)).add(unpaidPrincipal(i)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void markPaidStatus(LoanRepaymentSchedule i, LocalDate date) {
        i.setTotalPaid(i.getPrincipalPaid().add(i.getInterestPaid()).add(i.getFeesPaid()));
        if (unpaidFees(i).add(unpaidInterest(i)).add(unpaidPrincipal(i)).signum() == 0) {
            i.setStatus(LoanRepaymentSchedule.InstallmentStatus.PAID);
            i.setPaidDate(date);
        } else if (i.getTotalPaid().signum() > 0) {
            i.setStatus(LoanRepaymentSchedule.InstallmentStatus.PARTIALLY_PAID);
        }
    }

    private static void requireOpen(Loan loan, String action) {
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.IN_ARREARS) {
            throw CbaException.badRequest("INVALID_LOAN_STATE", "Only ACTIVE or IN_ARREARS loans can " + action);
        }
    }

    /** Defaults to today; never in the future or before disbursement. */
    private static LocalDate paymentDate(Loan loan, LocalDate requested) {
        LocalDate today = LocalDate.now();
        LocalDate date = requested != null ? requested : today;
        if (date.isAfter(today)) {
            throw CbaException.badRequest("FUTURE_PAYMENT_DATE", "Payment date " + date + " is in the future");
        }
        if (loan.getDisbursementDate() != null && date.isBefore(loan.getDisbursementDate())) {
            throw CbaException.badRequest("PAYMENT_BEFORE_DISBURSEMENT",
                    "Payment date " + date + " is before the disbursement on " + loan.getDisbursementDate());
        }
        return date;
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

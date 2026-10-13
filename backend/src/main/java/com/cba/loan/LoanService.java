package com.cba.loan;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.audit.AuditLogService;
import com.cba.charge.ChargeDefinition;
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
import com.cba.system.GlobalConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final LoanAccrualService loanAccrualService;
    private final LoanImpairmentService loanImpairmentService;
    private final GlobalConfigurationRepository globalConfigRepository;

    private static final String LOAN_TYPE = "LN";
    /** Global configuration: enforce CBN's "fully provided for at least a year" before a write-off. */
    static final String CBN_WRITE_OFF_RULES = "enforce-cbn-write-off-rules";

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

        BigDecimal amount = loan.getApprovedAmount();
        LocalDate today = LocalDate.now();

        // Origination fees: the product's fee plus disbursement charges added to the loan.
        // Deducted from the proceeds and deferred (IFRS 9 B5.4.2).
        List<LoanCharge> feeCharges = loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(id).stream()
            .filter(c -> c.getChargeTimeType() == ChargeDefinition.ChargeTimeType.DISBURSEMENT
                && !c.isPaid() && !c.isWaived())
            .toList();
        BigDecimal productFee = loan.getProduct().getOriginationFee() != null
            ? loan.getProduct().getOriginationFee() : BigDecimal.ZERO;
        BigDecimal fee = feeCharges.stream().map(LoanCharge::getAmountOutstanding)
            .reduce(productFee, BigDecimal::add);
        if (fee.compareTo(amount) >= 0) {
            throw CbaException.badRequest("ORIGINATION_FEE_EXCEEDS_PRINCIPAL",
                "Origination fees " + fee + " are not less than the amount lent " + amount);
        }

        // Credits the linked account with amount − fee (lock, status check, transaction
        // record) and posts DR portfolio / CR savings control / CR deferred loan fees.
        loanGlPosting.postDisbursement(loan, amount, fee, today);
        settleOriginationFees(loan, feeCharges, productFee);

        // Build repayment schedule; each period carries its effective-interest share of the fee.
        LocalDate firstDueDate = today.plusMonths(1);
        List<LoanRepaymentSchedule> schedule = scheduleEngine.generateAnnuitySchedule(
            loan, amount, loan.getInterestRate(), loan.getTermMonths(), firstDueDate);
        List<BigDecimal> feeShares = EffectiveInterest.feeAmortisation(amount, fee, today, schedule);
        for (int k = 0; k < schedule.size(); k++) {
            schedule.get(k).setFeeAmortisation(feeShares.get(k));
        }

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setOutstandingBalance(amount);
        loan.setDisbursementDate(today);
        loan.setInterestAccruedThrough(today);
        loan.setDeferredFee(fee);
        loan.setDeferredFeeRemaining(fee);
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

        if (loan.getStatus() == LoanStatus.WRITTEN_OFF) {
            return recover(loan, request);
        }
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.IN_ARREARS) {
            throw CbaException.badRequest("INVALID_LOAN_STATE",
                    "Loan must be ACTIVE, IN_ARREARS or WRITTEN_OFF to accept repayments");
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

        // Income up to the payment date first, so the receivable (and any suspense) is current.
        loanAccrualService.accrueThrough(loan, date);

        LoanGlPosting.Allocation allocation = allocate(loan, payment, date);
        loanGlPosting.postRepayment(loan, source, allocation, date, "RPMT",
                "Loan repayment " + loan.getLoanAccountNumber());
        releaseSuspense(loan, allocation.interest(), date);

        loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(allocation.principal()));
        if (loan.getOutstandingBalance().signum() <= 0) {
            loan.setOutstandingBalance(BigDecimal.ZERO);
            loan.setStatus(LoanStatus.CLOSED_OBLIGATIONS_MET);
            loanAccrualService.closeOut(loan, date);
            loanImpairmentService.release(loan, date);
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
     * Cash collected on a written-off loan. Its balances are off the books, so nothing is
     * cleared: the cash is an impairment gain (IFRS 9 §5.5.8). It is still applied to the
     * schedule, which keeps the borrower's legal debt, and capped at that debt.
     */
    private LoanRepaymentResponse recover(Loan loan, LoanRepaymentRequest request) {
        LocalDate date = paymentDate(loan, request.paymentDate());
        if (loan.getWrittenOffOn() != null && date.isBefore(loan.getWrittenOffOn())) {
            throw CbaException.badRequest("PAYMENT_BEFORE_WRITE_OFF",
                    "Payment date " + date + " is before the write-off on " + loan.getWrittenOffOn());
        }
        LoanPaymentSource source = LoanPaymentSource.of(
                request.paymentMethod(), request.sourceAccountId(), request.tellerSessionId());
        BigDecimal payment = request.amount();
        BigDecimal owed = scheduledOutstanding(loan);
        if (payment.compareTo(owed) > 0) {
            throw CbaException.badRequest("REPAYMENT_EXCEEDS_OUTSTANDING",
                    "Recovery " + payment + " exceeds the " + owed + " the borrower still owes");
        }
        LoanGlPosting.Allocation allocation = allocate(loan, payment, date);
        loanGlPosting.postRecovery(loan, source, payment, date);
        loan.setRecoveredAmount(loan.getRecoveredAmount().add(payment));

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loan.getId().toString(), "RECOVERY", null,
                Map.of("amount", payment, "recoveredToDate", saved.getRecoveredAmount(),
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

    /**
     * Interest and scheduled fees due by {@code date}, plus the interest earned so far in
     * the current period (already accrued as income) — but not interest of later days.
     */
    private ForeclosureQuote quote(Loan loan, LocalDate date) {
        BigDecimal interest = BigDecimal.ZERO, fees = BigDecimal.ZERO;
        LocalDate periodStart = loan.getDisbursementDate();
        for (LoanRepaymentSchedule i : loan.getRepaymentSchedule()) {
            LocalDate start = periodStart;
            periodStart = i.getDueDate();
            if (i.getStatus() == LoanRepaymentSchedule.InstallmentStatus.PAID) continue;
            if (i.getDueDate().isAfter(date)) {
                BigDecimal earned = start == null ? BigDecimal.ZERO
                    : LoanAccrualService.accruedBy(i.getInterestDue(), start, i.getDueDate(), date);
                interest = interest.add(earned.subtract(i.getInterestPaid()).max(BigDecimal.ZERO));
                continue;
            }
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

    /**
     * Writes a credit-impaired loan off (IFRS 9 §5.4.4): brings it current, remeasures its
     * expected credit losses, then derecognises its gross carrying amount against the
     * allowance. CBN: needs a board-approval reference and, unless {@value #CBN_WRITE_OFF_RULES}
     * is switched off, a prudential provision that has covered the principal for a year.
     * The balances written off are kept so the write-off can be undone.
     */
    @Transactional
    public LoanResponse writeOffLoan(UUID loanId, WriteOffRequest request) {
        Loan loan = findById(loanId);
        requireOpen(loan, "be written off");
        if (request.approvalReference() == null || request.approvalReference().isBlank()) {
            throw CbaException.badRequest("WRITE_OFF_APPROVAL_REQUIRED",
                    "A write-off needs the board (or delegated authority) approval reference");
        }
        LocalDate date = eventDate(loan, request.writeOffDate(), "Write-off");
        LoanStatus old = loan.getStatus();

        loanAccrualService.bringCurrent(loan, date);
        LoanImpairmentService.Measurement m = loanImpairmentService.remeasure(loan, date);
        if (m.stage() != 3) {
            throw CbaException.badRequest("WRITE_OFF_NOT_CREDIT_IMPAIRED",
                    "Loan " + loan.getLoanAccountNumber() + " is in stage " + m.stage() + " (" + loan.getDaysPastDue()
                            + " days past due): only a credit-impaired (stage 3) loan can be written off");
        }
        if (isConfigEnabled(CBN_WRITE_OFF_RULES)) {
            LocalDate since = loan.getFullyProvidedSince();
            if (since == null || since.isAfter(date.minusYears(1))) {
                throw CbaException.badRequest("WRITE_OFF_CBN_RULES_NOT_MET",
                        "CBN: a loan can be written off only after it has been fully provided for at least a year ("
                                + (since == null ? "it is not fully provided" : "fully provided since " + since)
                                + "). Switch off " + CBN_WRITE_OFF_RULES + " to override");
            }
        }

        BigDecimal fees = BigDecimal.ZERO, suspendedCharges = BigDecimal.ZERO;
        for (LoanCharge c : loanChargeRepository.findByLoanIdOrderByCreatedAtAsc(loanId)) {
            if (c.getIncomeRecognizedOn() == null || c.isPaid() || c.isWaived()) continue;
            fees = fees.add(c.getAmountOutstanding());
            if (c.isSuspended()) suspendedCharges = suspendedCharges.add(c.getAmountOutstanding());
        }
        LoanGlPosting.WriteOffAmounts amounts = new LoanGlPosting.WriteOffAmounts(
                loan.getOutstandingBalance(), LoanImpairmentService.interestReceivable(loan), fees,
                loan.getDeferredFeeRemaining(), loan.getInterestInSuspense().add(suspendedCharges),
                loan.getLossAllowance());
        String transactionId = loanGlPosting.postWriteOff(loan, amounts, date);

        Map<String, BigDecimal> snapshot = new LinkedHashMap<>();
        snapshot.put("principal", loan.getOutstandingBalance());
        snapshot.put("interestInSuspense", loan.getInterestInSuspense());
        snapshot.put("deferredFeeRemaining", loan.getDeferredFeeRemaining());
        snapshot.put("lossAllowance", loan.getLossAllowance());
        snapshot.put("prudentialProvision", loan.getPrudentialProvision());
        snapshot.put("eclStage", BigDecimal.valueOf(m.stage()));

        loan.setWriteOffSnapshot(snapshot);
        loan.setWriteOffTransactionId(transactionId);
        loan.setWrittenOffAmount(amounts.gross());
        loan.setRecoveredAmount(BigDecimal.ZERO);
        loan.setOutstandingBalance(BigDecimal.ZERO);
        loan.setInterestInSuspense(BigDecimal.ZERO);
        loan.setDeferredFeeRemaining(BigDecimal.ZERO);
        loan.setLossAllowance(BigDecimal.ZERO);
        loan.setPrudentialProvision(BigDecimal.ZERO);
        loan.setEclStage(null);
        loan.setStatus(LoanStatus.WRITTEN_OFF);
        loan.setWrittenOffOn(date);
        loan.setWriteOffReason(request.reason());
        loan.setWriteOffApprovalReference(request.approvalReference().trim());

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loanId.toString(), "WRITE_OFF", old.name(),
                Map.of("status", LoanStatus.WRITTEN_OFF.name(), "writtenOffAmount", amounts.gross(),
                        "allowanceUsed", amounts.allowance(), "approvalReference", saved.getWriteOffApprovalReference(),
                        "reason", request.reason()));
        log.info("Loan written off: {} — {} against an allowance of {}",
                saved.getLoanAccountNumber(), amounts.gross(), amounts.allowance());
        return toResponse(saved);
    }

    /**
     * Undoes a write-off made in error: the write-off journal is reversed line for line,
     * dated the write-off date (so a closed GL period refuses it), and the balances come
     * back. Not once cash has been recovered: those recoveries are already income.
     */
    @Transactional
    public LoanResponse undoWriteOff(UUID loanId) {
        Loan loan = findById(loanId);

        if (loan.getStatus() != LoanStatus.WRITTEN_OFF) {
            throw CbaException.badRequest("INVALID_LOAN_STATE", "Only WRITTEN_OFF loans can be un-written-off");
        }
        if (loan.getRecoveredAmount().signum() > 0) {
            throw CbaException.badRequest("WRITE_OFF_HAS_RECOVERIES",
                    loan.getRecoveredAmount() + " has been recovered on loan " + loan.getLoanAccountNumber()
                            + " since the write-off; it cannot be undone");
        }

        if (loan.getWriteOffTransactionId() != null) {
            loanGlPosting.postWriteOffReversal(loan, loan.getWriteOffTransactionId(), loan.getWrittenOffOn());
        }
        Map<String, BigDecimal> snapshot = loan.getWriteOffSnapshot();
        if (snapshot != null) {
            loan.setOutstandingBalance(snapshot.getOrDefault("principal", BigDecimal.ZERO));
            loan.setInterestInSuspense(snapshot.getOrDefault("interestInSuspense", BigDecimal.ZERO));
            loan.setDeferredFeeRemaining(snapshot.getOrDefault("deferredFeeRemaining", BigDecimal.ZERO));
            loan.setLossAllowance(snapshot.getOrDefault("lossAllowance", BigDecimal.ZERO));
            loan.setPrudentialProvision(snapshot.getOrDefault("prudentialProvision", BigDecimal.ZERO));
            BigDecimal stage = snapshot.get("eclStage");
            loan.setEclStage(stage != null ? stage.intValue() : null);
        } else {
            // Written off before write-offs posted to the GL: nothing to reverse.
            loan.setOutstandingBalance(loan.getRepaymentSchedule().stream()
                    .filter(s -> s.getStatus() != LoanRepaymentSchedule.InstallmentStatus.PAID)
                    .map(LoanService::unpaidPrincipal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        LoanAccrualService.updateArrears(loan, LocalDate.now());
        loan.setWrittenOffOn(null);
        loan.setWriteOffReason(null);
        loan.setWriteOffApprovalReference(null);
        loan.setWrittenOffAmount(null);
        loan.setWriteOffTransactionId(null);
        loan.setWriteOffSnapshot(null);

        Loan saved = loanRepository.save(loan);
        auditLogService.log("LOAN", loanId.toString(), "UNDO_WRITE_OFF", LoanStatus.WRITTEN_OFF.name(), saved.getStatus().name());
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
        // Income up to the foreclosure date first: the quote collects interest earned so far.
        loanAccrualService.accrueThrough(loan, date);
        ForeclosureQuote q = quote(loan, date);

        // The borrower pays the quote; it clears the portfolio, the receivables for
        // interest (due and accrued) and fees already due, and recognised charges.
        loanGlPosting.postRepayment(loan, source,
                new LoanGlPosting.Allocation(q.principal(), q.interest(), q.fees().add(q.charges())),
                date, "FCLS", "Loan foreclosure " + loan.getLoanAccountNumber());
        releaseSuspense(loan, q.interest(), date);

        // Instalments due by the date are paid in full. Later ones keep their principal
        // (paid now) and the interest earned to date; the rest of their interest and their
        // fees are cancelled: not yet earned, never recognised.
        BigDecimal cancelledInterest = BigDecimal.ZERO;
        for (LoanRepaymentSchedule i : loan.getRepaymentSchedule()) {
            if (i.getStatus() == LoanRepaymentSchedule.InstallmentStatus.PAID) continue;
            if (i.getDueDate().isAfter(date)) {
                BigDecimal kept = i.getInterestAccrued().max(i.getInterestPaid());
                cancelledInterest = cancelledInterest.add(i.getInterestDue().subtract(kept).max(BigDecimal.ZERO));
                i.setInterestDue(kept);
                i.setInterestPaid(kept);
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

        // Settled: the origination fee still deferred is income now; the allowance is released.
        loanAccrualService.closeOut(loan, date);
        loanImpairmentService.release(loan, date);

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

    /**
     * Interest received on a non-performing loan comes out of suspense into income: cash
     * basis (CBN). Up to what suspense holds; the rest was income already.
     */
    private void releaseSuspense(Loan loan, BigDecimal interestReceived, LocalDate date) {
        BigDecimal release = interestReceived.min(loan.getInterestInSuspense());
        if (release.signum() <= 0) return;
        loanGlPosting.postInterestSuspenseRelease(loan, release, date);
        loan.setInterestInSuspense(loan.getInterestInSuspense().subtract(release));
    }

    /**
     * Marks the disbursement charges paid (deducted from the proceeds) and records the
     * product's origination fee as a paid charge, so the charges tab shows what was taken.
     */
    private void settleOriginationFees(Loan loan, List<LoanCharge> feeCharges, BigDecimal productFee) {
        for (LoanCharge c : feeCharges) {
            c.setAmountPaid(c.getAmountPaid().add(c.getAmountOutstanding()));
            c.setAmountOutstanding(BigDecimal.ZERO);
            c.setPaid(true);
            loanChargeRepository.save(c);
        }
        if (productFee.signum() > 0) {
            LoanCharge c = new LoanCharge();
            c.setLoan(loan);
            c.setName("Origination fee");
            c.setCurrencyCode(LoanGlPosting.currency(loan));
            c.setChargeTimeType(ChargeDefinition.ChargeTimeType.DISBURSEMENT);
            c.setChargeCalculation(ChargeDefinition.ChargeCalculation.FLAT);
            c.setAmount(productFee);
            c.setAmountPaid(productFee);
            c.setAmountOutstanding(BigDecimal.ZERO);
            c.setPaid(true);
            loanChargeRepository.save(c);
        }
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

    /** A write-off (or other event) date: defaults to today; never in the future or before disbursement. */
    private static LocalDate eventDate(Loan loan, LocalDate requested, String what) {
        LocalDate today = LocalDate.now();
        LocalDate date = requested != null ? requested : today;
        if (date.isAfter(today)) {
            throw CbaException.badRequest("FUTURE_DATE", what + " date " + date + " is in the future");
        }
        if (loan.getDisbursementDate() != null && date.isBefore(loan.getDisbursementDate())) {
            throw CbaException.badRequest("DATE_BEFORE_DISBURSEMENT",
                    what + " date " + date + " is before the disbursement on " + loan.getDisbursementDate());
        }
        return date;
    }

    private boolean isConfigEnabled(String name) {
        return globalConfigRepository.findByName(name)
                .filter(c -> c.isEnabled() && Boolean.TRUE.equals(c.getBooleanValue())).isPresent();
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
            l.getDisbursementDate(), l.getMaturityDate(),
            l.getDaysPastDue(), l.isNonPerforming(), l.getInterestInSuspense(), l.getDeferredFeeRemaining(),
            l.getEclStage(), l.getLossAllowance(), l.getPrudentialProvision(),
            l.getWrittenOffOn(), l.getWriteOffReason(), l.getWriteOffApprovalReference(),
            l.getWrittenOffAmount(), l.getRecoveredAmount()
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

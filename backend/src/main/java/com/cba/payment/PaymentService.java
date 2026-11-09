package com.cba.payment;

import com.cba.account.Account;
import com.cba.account.AccountHoldRepository;
import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import com.cba.account.Transaction;
import com.cba.account.TransactionRepository;
import com.cba.account.TransactionType;
import com.cba.account.algorithm.AccountNumberAlgorithmService;
import com.cba.audit.AuditLogService;
import com.cba.fraud.FraudEngineService;
import com.cba.fraud.TransactionFraudEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import com.cba.common.exception.CbaException;
import com.cba.currency.ExchangeRateService;
import com.cba.currency.dto.ConversionResult;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.dto.ReversePaymentRequest;
import com.cba.payment.dto.StandingOrderRequest;
import com.cba.payment.dto.StandingOrderResponse;
import com.cba.payment.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository             paymentRepository;
    private final AccountRepository             accountRepository;
    private final AccountHoldRepository         accountHoldRepository;
    private final TransactionRepository         transactionRepository;
    private final AccountNumberAlgorithmService accountNumberAlgorithmService;
    private final AuditLogService               auditLogService;
    private final ExchangeRateService           exchangeRateService;
    private final StandingOrderRepository       standingOrderRepository;
    private final ApplicationEventPublisher     eventPublisher;
    private final com.cba.payment.gateway.ExternalPaymentGateway externalPaymentGateway;
    private final com.cba.account.AccountGlPosting accountGlPosting;
    private final com.cba.account.AccountService accountService;

    @Lazy
    @Autowired(required = false)
    private FraudEngineService fraudEngineService;

    /**
     * Double-entry internal transfer with cross-currency support.
     *
     * Same-currency: source debited and destination credited by the same amount.
     * Cross-currency: source debited in source currency; destination credited in
     *                 destination currency at the admin-configured exchange rate.
     *
     * Both accounts are locked with SELECT FOR UPDATE (deterministic UUID order)
     * to prevent deadlocks under concurrent requests.
     */
    @Transactional
    public PaymentResponse transfer(TransferRequest request, String createdBy) {
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw CbaException.badRequest("SAME_ACCOUNT_TRANSFER",
                "Source and destination accounts must differ");
        }

        // Validate external destination account number if provided
        accountNumberAlgorithmService.validatePaymentDestination(request.destinationAccountNumber());

        // Lock both accounts — order by UUID to prevent deadlocks
        UUID firstId  = min(request.sourceAccountId(), request.destinationAccountId());
        UUID secondId = max(request.sourceAccountId(), request.destinationAccountId());

        Account first  = accountRepository.findByIdWithLock(firstId)
            .orElseThrow(() -> CbaException.notFound("Account", firstId));
        Account second = accountRepository.findByIdWithLock(secondId)
            .orElseThrow(() -> CbaException.notFound("Account", secondId));

        Account source      = first.getId().equals(request.sourceAccountId())      ? first : second;
        Account destination = first.getId().equals(request.destinationAccountId()) ? first : second;

        validateAccountForDebit(source);
        validateAccountActive(destination);

        String srcCcy = source.getCurrencyCode();
        String dstCcy = destination.getCurrencyCode();
        boolean isCrossCurrency = !srcCcy.equalsIgnoreCase(dstCcy);

        // Resolve the amount to credit to the destination account
        BigDecimal creditAmount;
        ConversionResult conversion = null;

        if (isCrossCurrency) {
            conversion = exchangeRateService.convert(request.amount(), srcCcy, dstCcy);
            creditAmount = conversion.convertedAmount();
            log.info("Cross-currency transfer: {} {} → {} {} (rate={})",
                request.amount(), srcCcy, creditAmount, dstCcy, conversion.rateUsed());
        } else {
            creditAmount = request.amount();
        }

        // Create payment record
        Payment payment = new Payment();
        payment.setReferenceNumber("PAY-" + System.currentTimeMillis());
        payment.setPaymentType(PaymentType.INTERNAL_TRANSFER);
        payment.setSourceAccount(source);
        payment.setDestinationAccount(destination);
        payment.setAmount(request.amount());
        payment.setCurrencyCode(srcCcy);
        payment.setDescription(request.description());
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setCreatedBy(createdBy);
        payment.setCrossCurrency(isCrossCurrency);

        if (isCrossCurrency && conversion != null) {
            payment.setSourceCurrency(srcCcy);
            payment.setSourceAmount(request.amount());
            payment.setDestinationCurrency(dstCcy);
            payment.setDestinationAmount(creditAmount);
            payment.setExchangeRateUsed(conversion.rateUsed());
        }

        payment = paymentRepository.save(payment);

        // Apply double-entry: debit source in source currency, credit destination in its currency
        BigDecimal srcBefore = source.getBalance();
        BigDecimal dstBefore = destination.getBalance();
        BigDecimal srcOnHold = accountHoldRepository.sumActiveHoldsByAccount(source.getId());
        BigDecimal srcEffectiveAvailable = source.getBalance().subtract(srcOnHold)
                .subtract(source.computeEffectiveFloor());
        source.debit(request.amount(), srcEffectiveAvailable);
        destination.credit(creditAmount);
        accountRepository.save(source);
        accountRepository.save(destination);

        // Same transaction as the balance change: a missing GL mapping, exchange rate or
        // functional currency rejects the transfer instead of leaving it unposted.
        String ref = payment.getReferenceNumber();
        accountGlPosting.postTransfer(source, srcBefore, destination, dstBefore, LocalDate.now(),
            "Transfer " + source.getAccountNumber() + " → " + destination.getAccountNumber(), ref, payment.getId());

        // Immutable transaction records (each account's ledger in its own currency)
        transactionRepository.save(Transaction.of(source, TransactionType.TRANSFER_DEBIT,
            request.amount(), source.getBalance(),
            "Transfer to " + destination.getAccountNumber(), ref, createdBy));
        transactionRepository.save(Transaction.of(destination, TransactionType.TRANSFER_CREDIT,
            creditAmount, destination.getBalance(),
            "Transfer from " + source.getAccountNumber(), ref, createdBy));

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setExecutedDate(Instant.now());
        Payment completed = paymentRepository.save(payment);

        UUID sourceCustomerId = source.getCustomer() != null ? source.getCustomer().getId() : null;
        eventPublisher.publishEvent(new TransactionFraudEvent(
            sourceCustomerId, source.getId(), completed.getId(),
            request.amount(), srcCcy, "TRANSFER"));
        auditLogService.log("PAYMENT", completed.getId().toString(), "TRANSFER_EXECUTED", null, request);
        log.info("Transfer completed: {} — {} {} → {} {} from {} to {}",
            ref, request.amount(), srcCcy, creditAmount, dstCcy,
            source.getAccountNumber(), destination.getAccountNumber());

        return toResponse(completed);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        return toResponse(paymentRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Payment", id)));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAccountPayments(UUID accountId, Pageable pageable) {
        return paymentRepository
            .findBySourceAccountIdOrDestinationAccountId(accountId, accountId, pageable)
            .map(this::toResponse);
    }

    // ── Standing Orders ──────────────────────────────────────────────────────

    @Transactional
    public StandingOrderResponse createStandingOrder(StandingOrderRequest request) {
        Account source = accountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> CbaException.notFound("Account", request.sourceAccountId()));
        Account dest = accountRepository.findById(request.destinationAccountId())
                .orElseThrow(() -> CbaException.notFound("Account", request.destinationAccountId()));

        if (source.getStatus() != AccountStatus.ACTIVE)
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE", "Source account is not active");
        if (dest.getStatus() != AccountStatus.ACTIVE)
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE", "Destination account is not active");

        StandingOrder so = new StandingOrder();
        so.setSourceAccount(source);
        so.setDestinationAccount(dest);
        so.setAmount(request.amount());
        so.setCurrencyCode(request.currencyCode() != null ? request.currencyCode().toUpperCase(Locale.ROOT) : source.getCurrencyCode());
        so.setFrequency(request.frequency());
        so.setStartDate(request.startDate());
        so.setEndDate(request.endDate());
        so.setNextExecutionDate(request.startDate());
        so.setDescription(request.description());

        StandingOrder saved = standingOrderRepository.save(so);
        auditLogService.log("StandingOrder", saved.getId().toString(), "CREATE", null, saved);
        return StandingOrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public java.util.List<StandingOrderResponse> listStandingOrders(UUID accountId) {
        return standingOrderRepository.findBySourceAccountId(accountId)
                .stream().map(StandingOrderResponse::from).toList();
    }

    @Transactional
    public StandingOrderResponse cancelStandingOrder(UUID id) {
        StandingOrder so = standingOrderRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("StandingOrder", id));
        so.setStatus(StandingOrder.Status.CANCELLED);
        StandingOrder saved = standingOrderRepository.save(so);
        auditLogService.log("StandingOrder", id.toString(), "CANCEL", null, saved);
        return StandingOrderResponse.from(saved);
    }

    // ── Payment Reversal ─────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse reversePayment(UUID paymentId, ReversePaymentRequest request, String reversedBy) {
        Payment original = paymentRepository.findById(paymentId)
                .orElseThrow(() -> CbaException.notFound("Payment", paymentId));

        if (original.getStatus() != PaymentStatus.COMPLETED)
            throw CbaException.badRequest("PAYMENT_NOT_REVERSIBLE",
                    "Only COMPLETED payments can be reversed");
        if (original.getReversalOf() != null)
            throw CbaException.badRequest("ALREADY_A_REVERSAL",
                    "Cannot reverse a reversal payment");
        if (original.getPaymentType() == PaymentType.EXTERNAL_PAYMENT)
            // Money that left the bank isn't pulled back by reversing a journal: a return comes from
            // the beneficiary's bank, through the gateway callback or ops (ExternalPaymentOutcomeService).
            throw CbaException.badRequest("EXTERNAL_PAYMENT_NOT_REVERSIBLE",
                    "External payments can't be reversed; a return is recorded through the gateway callback or the external-outcome endpoint");

        // Lock accounts in consistent order
        UUID srcId = original.getSourceAccount().getId();
        UUID dstId = original.getDestinationAccount().getId();
        Account src = accountRepository.findByIdWithLock(min(srcId, dstId)).orElseThrow();
        Account dst = accountRepository.findByIdWithLock(max(srcId, dstId)).orElseThrow();
        // Re-assign to correct roles
        if (!src.getId().equals(srcId)) { Account tmp = src; src = dst; dst = tmp; }

        // Swap: credit back source, debit destination, each in its own currency. A
        // cross-currency payment credited the destination its converted amount, so that
        // is what comes back out of it (not the source-currency amount).
        BigDecimal srcAmount = original.getAmount();
        BigDecimal dstAmount = original.isCrossCurrency() && original.getDestinationAmount() != null
                ? original.getDestinationAmount() : original.getAmount();
        BigDecimal srcBefore = src.getBalance();
        BigDecimal dstBefore = dst.getBalance();
        src.credit(srcAmount);
        BigDecimal dstOnHold = accountHoldRepository.sumActiveHoldsByAccount(dst.getId());
        BigDecimal dstEffectiveAvailable = dst.getBalance().subtract(dstOnHold)
                .subtract(dst.computeEffectiveFloor());
        dst.debit(dstAmount, dstEffectiveAvailable);
        accountRepository.save(src);
        accountRepository.save(dst);

        String ref = "REV-" + original.getReferenceNumber();
        Transaction srcTx = Transaction.of(src, TransactionType.TRANSFER_CREDIT, srcAmount,
                src.getBalance(), "Reversal of " + original.getReferenceNumber(), ref, reversedBy);
        Transaction dstTx = Transaction.of(dst, TransactionType.TRANSFER_DEBIT, dstAmount,
                dst.getBalance(), "Reversal of " + original.getReferenceNumber(), ref, reversedBy);
        transactionRepository.save(srcTx);
        transactionRepository.save(dstTx);

        // Mark original as reversed
        original.setStatus(PaymentStatus.REVERSED);
        original.setReversalReason(request.reason());
        original.setReversedAt(Instant.now());
        paymentRepository.save(original);

        // Create reversal payment record
        Payment reversal = new Payment();
        reversal.setReferenceNumber(ref);
        reversal.setSourceAccount(original.getDestinationAccount());
        reversal.setDestinationAccount(original.getSourceAccount());
        reversal.setAmount(dstAmount);
        reversal.setCurrencyCode(dst.getCurrencyCode());
        if (original.isCrossCurrency()) {
            reversal.setCrossCurrency(true);
            reversal.setSourceCurrency(dst.getCurrencyCode());
            reversal.setSourceAmount(dstAmount);
            reversal.setDestinationCurrency(src.getCurrencyCode());
            reversal.setDestinationAmount(srcAmount);
            reversal.setExchangeRateUsed(original.getExchangeRateUsed());
        }
        reversal.setPaymentType(PaymentType.INTERNAL_TRANSFER);
        reversal.setStatus(PaymentStatus.COMPLETED);
        reversal.setDescription("Reversal: " + request.reason());
        reversal.setExecutedDate(Instant.now());
        reversal.setReversalOf(original);
        Payment saved = paymentRepository.save(reversal);

        // The reversal is a new transaction on today's date: customer amounts come back
        // exactly, FX equivalents use today's spot rate and any difference is realised
        // (IAS 21 §21, §28). The original journal stays as posted.
        accountGlPosting.postTransfer(dst, dstBefore, src, srcBefore, LocalDate.now(),
                "Reversal of " + original.getReferenceNumber(), ref, saved.getId());

        auditLogService.log("Payment", paymentId.toString(), "REVERSE", PaymentStatus.COMPLETED.name(), PaymentStatus.REVERSED.name());
        log.info("Payment reversed: {} by {}", original.getReferenceNumber(), reversedBy);

        // Notify downstream (AFTER_COMMIT) — e.g. an Open Banking listener fires PAYMENT.REVERSED
        // to the initiating partner when the original payment's actor is "open-banking:{consentId}".
        eventPublisher.publishEvent(new PaymentReversedEvent(
                original.getId(), original.getCreatedBy(), original.getAmount(), ref, request.reason()));

        return toResponse(saved);
    }

    private void validateAccountForDebit(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE",
                "Source account " + account.getAccountNumber() + " is not active");
        }
    }

    private void validateAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE",
                "Destination account " + account.getAccountNumber() + " is not active");
        }
    }

    private UUID min(UUID a, UUID b) { return a.compareTo(b) <= 0 ? a : b; }
    private UUID max(UUID a, UUID b) { return a.compareTo(b) >= 0 ? a : b; }

    PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
            p.getId(), p.getReferenceNumber(), p.getPaymentType(),
            p.getSourceAccount() != null ? p.getSourceAccount().getId() : null,
            p.getSourceAccount() != null ? p.getSourceAccount().getAccountNumber() : null,
            p.getDestinationAccount() != null ? p.getDestinationAccount().getId() : null,
            p.getDestinationAccount() != null ? p.getDestinationAccount().getAccountNumber() : null,
            p.getAmount(), p.getCurrencyCode(), p.getDescription(),
            p.getStatus(), p.getExecutedDate(), p.getCreatedAt(),
            p.isCrossCurrency(), p.getSourceCurrency(), p.getSourceAmount(),
            p.getDestinationCurrency(), p.getDestinationAmount(), p.getExchangeRateUsed(),
            p.getExternalNetwork(), p.getBeneficiaryName(), p.getBeneficiaryIban(),
            p.getBeneficiaryBic(), p.getBeneficiaryBankName(), p.getBeneficiaryCountryCode(),
            p.getExternalReference(), p.getChargeType(),
            p.getNetworkReference(), p.getSubmitOutcome(), p.getSettledAt(), p.getOutcomeSource(),
            p.getFailureReason()
        );
    }

    // ── External / SWIFT / SEPA Payments ─────────────────────────────────────

    /**
     * An outbound SWIFT/SEPA/ACH payment (backlog 1). In one transaction:
     * <ol>
     *   <li>debit the source under the withdrawal rules (row lock, holds, floor, lock-in) and post
     *       DR deposit / CR 2400 Outgoing Payments in Transit;</li>
     *   <li>submit to the gateway. REJECTED (a definite refusal) throws and everything rolls back.
     *       ACCEPTED leaves the payment PROCESSING until the gateway's signed callback or ops settle
     *       or return it ({@link ExternalPaymentOutcomeService}); a gateway that settles at once
     *       (the simulated one) also posts DR 2400 / CR nostro and completes. UNKNOWN (timeout, 5xx)
     *       keeps the debit and waits too: the instruction may have gone out, and rolling back could
     *       let money leave the bank with no debit.</li>
     * </ol>
     * The debit comes before the submit so that a refused debit never reaches the network.
     */
    @Transactional
    public PaymentResponse initiateExternalPayment(
            com.cba.payment.dto.ExternalPaymentRequest req, String actor) {

        Account source = accountRepository.findById(req.sourceAccountId())
                .orElseThrow(() -> CbaException.notFound("Account", req.sourceAccountId().toString()));
        String currency = req.currencyCode().trim().toUpperCase(Locale.ROOT);
        if (!currency.equals(source.getCurrencyCode())) {
            throw CbaException.badRequest("CURRENCY_MISMATCH",
                    "The source account is in " + source.getCurrencyCode() + "; external payments are made in the account's currency");
        }

        String ref = "EXT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String network = req.network().toUpperCase(Locale.ROOT);
        String chargeType = req.chargeType() != null ? req.chargeType().toUpperCase(Locale.ROOT) : "SHA";

        Payment payment = new Payment();
        payment.setPaymentType(PaymentType.EXTERNAL_PAYMENT);
        payment.setSourceAccount(source);
        payment.setAmount(req.amount());
        payment.setCurrencyCode(currency);
        payment.setDescription(req.description());
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setReferenceNumber(ref);
        payment.setCreatedBy(actor);
        payment.setExternalNetwork(network);
        payment.setBeneficiaryName(req.beneficiaryName());
        payment.setBeneficiaryIban(req.beneficiaryIban());
        payment.setBeneficiaryBic(req.beneficiaryBic());
        payment.setBeneficiaryBankName(req.beneficiaryBankName());
        payment.setBeneficiaryCountryCode(req.beneficiaryCountryCode());
        payment.setExternalReference(req.externalReference() != null && !req.externalReference().isBlank()
                ? req.externalReference() : null);
        payment.setChargeType(chargeType);
        payment = paymentRepository.save(payment);

        com.cba.account.AccountService.BalanceChange debit = accountService.debitForSubledger(
                source.getId(), req.amount(), TransactionType.TRANSFER_DEBIT,
                "External " + network + " payment to " + req.beneficiaryName(), ref, actor);
        LocalDate today = LocalDate.now();
        accountGlPosting.postExternalPaymentMovement(debit.account(), debit.before(), today,
                "External " + network + " payment " + ref, ref, payment.getId());

        com.cba.payment.gateway.GatewayResult gw = externalPaymentGateway.submit(
                new com.cba.payment.gateway.ExternalPaymentInstruction(
                        network, req.amount(), currency,
                        req.beneficiaryName(), req.beneficiaryIban(), req.beneficiaryBic(),
                        req.beneficiaryBankName(), req.beneficiaryCountryCode(), chargeType, ref));
        if (gw.status() == com.cba.payment.gateway.GatewayResult.Status.REJECTED) {
            throw CbaException.badRequest("EXTERNAL_PAYMENT_REJECTED",
                    "External payments gateway rejected the transfer: " + gw.errorMessage());
        }

        payment.setSubmitOutcome(gw.accepted() ? "ACCEPTED" : "UNKNOWN");
        if (gw.networkReference() != null && !gw.networkReference().isBlank()) {
            payment.setNetworkReference(gw.networkReference());
            if (payment.getExternalReference() == null) payment.setExternalReference(gw.networkReference());
        }
        if (gw.settled()) {
            accountGlPosting.postExternalPaymentSettlement(req.amount(), currency, today,
                    "External payment " + ref + " settled", ref, payment.getId());
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExecutedDate(Instant.now());
            payment.setSettledAt(Instant.now());
            payment.setOutcomeSource("GATEWAY");
        }
        Payment saved = paymentRepository.save(payment);

        UUID customerId = source.getCustomer() != null ? source.getCustomer().getId() : null;
        eventPublisher.publishEvent(new TransactionFraudEvent(
                customerId, source.getId(), saved.getId(), req.amount(), currency, "EXTERNAL_PAYMENT"));
        auditLogService.log("PAYMENT", saved.getId().toString(), "EXTERNAL_PAYMENT_INITIATED",
                null, "network=" + network + ",status=" + saved.getStatus()
                        + ",gateway=" + externalPaymentGateway.gatewayId()
                        + ",submit=" + gw.status() + ",networkRef=" + gw.networkReference());
        if (gw.status() == com.cba.payment.gateway.GatewayResult.Status.UNKNOWN) {
            log.warn("External payment {} submit outcome unknown ({}): kept PROCESSING with the debit", ref, gw.errorCode());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PaymentResponse> listExternalPayments(
            PaymentStatus status, org.springframework.data.domain.Pageable pageable) {
        return (status == null
                ? paymentRepository.findByPaymentType(PaymentType.EXTERNAL_PAYMENT, pageable)
                : paymentRepository.findByPaymentTypeAndStatus(PaymentType.EXTERNAL_PAYMENT, status, pageable))
                .map(this::toResponse);
    }
}

package com.cba.payment;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.account.AccountStatus;
import com.cba.account.Transaction;
import com.cba.account.TransactionRepository;
import com.cba.account.TransactionType;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.currency.ExchangeRateService;
import com.cba.currency.dto.ConversionResult;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogService auditLogService;
    private final ExchangeRateService exchangeRateService;

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
        source.debit(request.amount());
        destination.credit(creditAmount);
        accountRepository.save(source);
        accountRepository.save(destination);

        // Immutable transaction records (each account's ledger in its own currency)
        String ref = payment.getReferenceNumber();
        transactionRepository.save(Transaction.of(source, TransactionType.TRANSFER_DEBIT,
            request.amount(), source.getBalance(),
            "Transfer to " + destination.getAccountNumber(), ref, createdBy));
        transactionRepository.save(Transaction.of(destination, TransactionType.TRANSFER_CREDIT,
            creditAmount, destination.getBalance(),
            "Transfer from " + source.getAccountNumber(), ref, createdBy));

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setExecutedDate(Instant.now());
        Payment completed = paymentRepository.save(payment);

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
            p.getDestinationCurrency(), p.getDestinationAmount(), p.getExchangeRateUsed()
        );
    }
}

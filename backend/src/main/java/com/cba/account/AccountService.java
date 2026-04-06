package com.cba.account;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.account.dto.AccountResponse;
import com.cba.account.dto.OpenAccountRequest;
import com.cba.account.dto.TransactionResponse;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.customer.KycStatus;
import com.cba.notification.AccountEvent;
import com.cba.product.DepositProduct;
import com.cba.product.DepositProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final DepositProductRepository depositProductRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AccountResponse openAccount(OpenAccountRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
            .orElseThrow(() -> CbaException.notFound("Customer", request.customerId()));

        if (customer.getKycStatus() != KycStatus.ACTIVE) {
            throw CbaException.badRequest("CUSTOMER_NOT_KYC_ACTIVE",
                "Customer must complete KYC before opening an account");
        }

        DepositProduct product = depositProductRepository.findById(request.productId())
            .orElseThrow(() -> CbaException.notFound("DepositProduct", request.productId()));

        Account account = new Account();
        account.setAccountNumber(accountNumberGenerator.generate(request.accountType()));
        account.setCustomer(customer);
        account.setProduct(product);
        account.setAccountType(request.accountType());
        account.setCurrencyCode(request.currencyCode() != null ? request.currencyCode() : "USD");

        Account saved = accountRepository.save(account);

        auditLogService.log("ACCOUNT", saved.getId().toString(), "OPENED", null, request);
        eventPublisher.publishEvent(new AccountEvent(this, saved.getId(), AccountEvent.Type.OPENED));

        log.info("Account opened: {} for customer {}", saved.getAccountNumber(), customer.getExternalId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> getCustomerAccounts(UUID customerId, Pageable pageable) {
        return accountRepository.findByCustomerId(customerId, pageable).map(this::toResponse);
    }

    @Transactional
    public AccountResponse updateStatus(UUID id, AccountStatus newStatus) {
        Account account = findById(id);
        AccountStatus oldStatus = account.getStatus();

        if (newStatus == AccountStatus.CLOSED) {
            if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw CbaException.badRequest("ACCOUNT_NOT_ZERO_BALANCE",
                    "Account balance must be zero before closing");
            }
            account.setClosedDate(LocalDate.now());
        }

        account.setStatus(newStatus);
        Account saved = accountRepository.save(account);
        auditLogService.log("ACCOUNT", id.toString(), "STATUS_CHANGED",
            oldStatus.name(), newStatus.name());

        return toResponse(saved);
    }

    @Transactional
    public TransactionResponse deposit(UUID accountId, BigDecimal amount, String description, String createdBy) {
        Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> CbaException.notFound("Account", accountId));

        validateAccountActive(account);
        account.credit(amount);
        accountRepository.save(account);

        Transaction tx = Transaction.of(account, TransactionType.DEPOSIT, amount,
            account.getBalance(), description, generateReference(), createdBy);
        return toTransactionResponse(transactionRepository.save(tx));
    }

    @Transactional
    public TransactionResponse withdraw(UUID accountId, BigDecimal amount, String description, String createdBy) {
        Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> CbaException.notFound("Account", accountId));

        validateAccountActive(account);
        account.debit(amount); // throws if insufficient balance
        accountRepository.save(account);

        Transaction tx = Transaction.of(account, TransactionType.WITHDRAWAL, amount,
            account.getBalance(), description, generateReference(), createdBy);
        return toTransactionResponse(transactionRepository.save(tx));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(UUID accountId, Pageable pageable) {
        return transactionRepository.findByAccountId(accountId, pageable)
            .map(this::toTransactionResponse);
    }

    private void validateAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE",
                "Account " + account.getAccountNumber() + " is " + account.getStatus());
        }
    }

    private String generateReference() {
        return "TXN-" + System.currentTimeMillis();
    }

    private Account findById(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Account", id));
    }

    AccountResponse toResponse(Account a) {
        String customerName = a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName();
        return new AccountResponse(
            a.getId(), a.getAccountNumber(), a.getCustomer().getId(),
            customerName, a.getProduct().getName(),
            a.getAccountType(), a.getStatus(), a.getBalance(),
            a.getCurrencyCode(), a.getOpenedDate(), a.getClosedDate(), a.getCreatedAt()
        );
    }

    TransactionResponse toTransactionResponse(Transaction t) {
        return new TransactionResponse(
            t.getId(), t.getTransactionType(), t.getAmount(),
            t.getRunningBalance(), t.getCurrencyCode(),
            t.getDescription(), t.getReferenceNumber(),
            t.getTransactionDate(), t.getValueDate()
        );
    }
}

package com.cba.account;

import com.cba.account.algorithm.AccountNumberAlgorithmService;
import com.cba.account.dto.*;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.common.tenant.TenantContext;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.customer.KycStatus;
import com.cba.notification.AccountEvent;
import com.cba.product.DepositProduct;
import com.cba.product.DepositProductRepository;
import com.cba.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final DepositProductRepository depositProductRepository;
    private final AccountHoldRepository accountHoldRepository;
    private final AccountNumberAlgorithmService accountNumberAlgorithmService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantService tenantService;

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
        account.setAccountNumber(accountNumberAlgorithmService.generate(request.accountType(), "001"));
        account.setCustomer(customer);
        account.setProduct(product);
        account.setAccountType(request.accountType());
        String resolvedCurrency = request.currencyCode() != null
            ? request.currencyCode().toUpperCase()
            : tenantService.getBaseCurrency(TenantContext.getTenant());
        account.setCurrencyCode(resolvedCurrency);

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
            BigDecimal activeHolds = accountHoldRepository.sumActiveHoldsByAccount(id);
            if (activeHolds.compareTo(BigDecimal.ZERO) > 0) {
                throw CbaException.badRequest("ACCOUNT_HAS_ACTIVE_HOLDS",
                    "Release all holds before closing the account");
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
    public AccountResponse approveAccount(UUID id) {
        Account account = findById(id);
        if (account.getStatus() != AccountStatus.SUBMITTED) {
            throw CbaException.badRequest("INVALID_TRANSITION",
                "Only SUBMITTED accounts can be approved (current: " + account.getStatus() + ")");
        }
        account.setStatus(AccountStatus.APPROVED);
        Account saved = accountRepository.save(account);
        auditLogService.log("ACCOUNT", id.toString(), "APPROVED",
            AccountStatus.SUBMITTED.name(), AccountStatus.APPROVED.name());
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse activateAccount(UUID id) {
        Account account = findById(id);
        if (account.getStatus() != AccountStatus.APPROVED) {
            throw CbaException.badRequest("INVALID_TRANSITION",
                "Only APPROVED accounts can be activated (current: " + account.getStatus() + ")");
        }
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        auditLogService.log("ACCOUNT", id.toString(), "ACTIVATED",
            AccountStatus.APPROVED.name(), AccountStatus.ACTIVE.name());
        eventPublisher.publishEvent(new AccountEvent(this, saved.getId(), AccountEvent.Type.OPENED));
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse rejectAccount(UUID id) {
        Account account = findById(id);
        if (account.getStatus() != AccountStatus.SUBMITTED) {
            throw CbaException.badRequest("INVALID_TRANSITION",
                "Only SUBMITTED accounts can be rejected (current: " + account.getStatus() + ")");
        }
        account.setStatus(AccountStatus.REJECTED);
        Account saved = accountRepository.save(account);
        auditLogService.log("ACCOUNT", id.toString(), "REJECTED",
            AccountStatus.SUBMITTED.name(), AccountStatus.REJECTED.name());
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse reactivateAccount(UUID id) {
        Account account = findById(id);
        if (account.getStatus() != AccountStatus.DORMANT) {
            throw CbaException.badRequest("INVALID_TRANSITION",
                "Only DORMANT accounts can be reactivated (current: " + account.getStatus() + ")");
        }
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        auditLogService.log("ACCOUNT", id.toString(), "REACTIVATED",
            AccountStatus.DORMANT.name(), AccountStatus.ACTIVE.name());
        return toResponse(saved);
    }

    // ── Teller operations ─────────────────────────────────────────────────────

    @Transactional
    public TransactionResponse deposit(UUID accountId, BigDecimal amount, String description, String createdBy) {
        Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> CbaException.notFound("Account", accountId));

        // Allow deposits on DORMANT accounts (credits can always come in)
        if (account.getStatus() != AccountStatus.ACTIVE && account.getStatus() != AccountStatus.DORMANT) {
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE",
                "Account " + account.getAccountNumber() + " is " + account.getStatus());
        }

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

        BigDecimal onHold = accountHoldRepository.sumActiveHoldsByAccount(accountId);
        BigDecimal available = account.getBalance().subtract(onHold);
        BigDecimal floor = account.computeEffectiveFloor();
        BigDecimal effectiveAvailable = available.subtract(floor);
        if (effectiveAvailable.compareTo(amount) < 0) {
            String msg = floor.compareTo(BigDecimal.ZERO) > 0
                ? "Withdrawal would breach minimum balance requirement of " + floor
                : "Insufficient available balance (available: " + effectiveAvailable + ")";
            throw CbaException.badRequest("BELOW_MINIMUM_BALANCE", msg);
        }
        account.debit(amount, effectiveAvailable);
        accountRepository.save(account);

        Transaction tx = Transaction.of(account, TransactionType.WITHDRAWAL, amount,
            account.getBalance(), description, generateReference(), createdBy);
        return toTransactionResponse(transactionRepository.save(tx));
    }

    // ── Holds ─────────────────────────────────────────────────────────────────

    @Transactional
    public AccountHoldResponse placeHold(UUID accountId, AccountHoldRequest request, String createdBy) {
        Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> CbaException.notFound("Account", accountId));

        validateAccountActive(account);

        BigDecimal onHold = accountHoldRepository.sumActiveHoldsByAccount(accountId);
        BigDecimal available = account.getBalance().subtract(onHold);
        BigDecimal effectiveAvailable = available.subtract(account.computeEffectiveFloor());
        if (effectiveAvailable.compareTo(request.amount()) < 0) {
            throw CbaException.badRequest("INSUFFICIENT_AVAILABLE_BALANCE",
                "Cannot place hold of " + request.amount() + "; effective available balance is " + effectiveAvailable);
        }

        AccountHold hold = new AccountHold();
        hold.setAccount(account);
        hold.setAmount(request.amount());
        hold.setReason(request.reason());
        hold.setExpiryDate(request.expiryDate());
        hold.setReferenceNumber("HLD-" + System.currentTimeMillis());
        hold.setCreatedBy(createdBy);

        AccountHold saved = accountHoldRepository.save(hold);
        auditLogService.log("ACCOUNT_HOLD", saved.getId().toString(), "HOLD_PLACED", null,
            "amount=" + request.amount() + " reason=" + request.reason());

        log.info("Hold placed on account {}: {} ({})", account.getAccountNumber(), request.amount(), request.reason());
        return toHoldResponse(saved);
    }

    @Transactional
    public AccountHoldResponse releaseHold(UUID accountId, UUID holdId, String releasedBy) {
        Account account = findById(accountId);
        AccountHold hold = accountHoldRepository.findById(holdId)
            .orElseThrow(() -> CbaException.notFound("AccountHold", holdId));

        if (!hold.getAccount().getId().equals(accountId)) {
            throw CbaException.notFound("AccountHold", holdId);
        }
        if (hold.getStatus() != AccountHoldStatus.ACTIVE) {
            throw CbaException.badRequest("HOLD_NOT_ACTIVE",
                "Hold " + holdId + " is already " + hold.getStatus());
        }

        hold.setStatus(AccountHoldStatus.RELEASED);
        hold.setReleasedAt(Instant.now());
        hold.setReleasedBy(releasedBy);

        AccountHold saved = accountHoldRepository.save(hold);
        auditLogService.log("ACCOUNT_HOLD", holdId.toString(), "HOLD_RELEASED",
            AccountHoldStatus.ACTIVE.name(), AccountHoldStatus.RELEASED.name());

        log.info("Hold released on account {}: {} ({})", account.getAccountNumber(), hold.getAmount(), hold.getReason());
        return toHoldResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountHoldResponse> getHolds(UUID accountId) {
        findById(accountId); // existence check
        return accountHoldRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
            .stream().map(this::toHoldResponse).toList();
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(UUID accountId, Pageable pageable) {
        return transactionRepository.findByAccountId(accountId, pageable)
            .map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByType(UUID accountId, TransactionType type, Pageable pageable) {
        return transactionRepository.findByAccountIdAndTransactionType(accountId, type, pageable)
            .map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByDateRange(
            UUID accountId, LocalDate from, LocalDate to, Pageable pageable) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return transactionRepository.findByAccountIdAndTransactionDateBetween(accountId, fromInstant, toInstant, pageable)
            .map(this::toTransactionResponse);
    }

    /** Returns available deposit products and account types for the new-account form. */
    @Transactional(readOnly = true)
    public Map<String, Object> getOpenAccountTemplate() {
        List<com.cba.product.DepositProduct> products = depositProductRepository.findAll();
        List<Map<String, Object>> productList = products.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("shortName", p.getShortName());
            m.put("accountType", p.getAccountType());
            m.put("interestRate", p.getInterestRate());
            m.put("interestCompounding", p.getInterestCompounding());
            m.put("minimumBalance", p.getMinimumBalance());
            m.put("allowOverdraft", p.isAllowOverdraft());
            m.put("currencyCode", p.getCurrencyCode());
            return m;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("depositProducts", productList);
        result.put("accountTypes", AccountType.values());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAccountTemplate(UUID id) {
        Account account = findById(id);
        DepositProduct product = account.getProduct();
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("productId", product.getId());
        template.put("productName", product.getName());
        template.put("shortName", product.getShortName());
        template.put("accountType", account.getAccountType());
        template.put("interestRate", product.getInterestRate());
        template.put("interestCompounding", product.getInterestCompounding());
        template.put("interestPostingPeriodType", product.getInterestPostingPeriodType());
        template.put("minimumBalance", product.getMinimumBalance());
        template.put("minRequiredOpeningBalance", product.getMinRequiredOpeningBalance());
        template.put("allowOverdraft", product.isAllowOverdraft());
        template.put("overdraftLimit", product.getOverdraftLimit());
        template.put("currency", account.getCurrencyCode());
        return template;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE",
                "Account " + account.getAccountNumber() + " is " + account.getStatus());
        }
    }

    // ── Interest operations ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> calculateInterest(UUID id) {
        Account account = findById(id);
        if (account.getStatus() != AccountStatus.ACTIVE)
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE",
                "Interest can only be calculated on ACTIVE accounts");
        BigDecimal rate = account.getProduct() != null ? account.getProduct().getInterestRate() : BigDecimal.ZERO;
        BigDecimal interest = computeDailyInterest(account);
        return Map.of(
            "accountId",             id,
            "accountNumber",         account.getAccountNumber(),
            "currentBalance",        account.getBalance(),
            "annualInterestRate",    rate != null ? rate : BigDecimal.ZERO,
            "projectedDailyInterest", interest
        );
    }

    @Transactional
    public AccountResponse postInterest(UUID id) {
        Account account = accountRepository.findByIdWithLock(id)
            .orElseThrow(() -> CbaException.notFound("Account", id));
        if (account.getStatus() != AccountStatus.ACTIVE)
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE",
                "Interest can only be posted to ACTIVE accounts");
        BigDecimal interest = computeDailyInterest(account);
        if (interest.compareTo(BigDecimal.ZERO) <= 0)
            throw CbaException.badRequest("NO_INTEREST_DUE",
                "No interest to post — zero balance or zero rate");
        account.setBalance(account.getBalance().add(interest));
        accountRepository.save(account);
        transactionRepository.save(Transaction.of(
            account, TransactionType.INTEREST_CREDIT, interest,
            account.getBalance(), "Manual interest posting",
            "INT-MANUAL-" + System.currentTimeMillis() + "-" + id.toString().substring(0, 8),
            "system"
        ));
        auditLogService.log("ACCOUNT", id.toString(), "POST_INTEREST",
            "MANUAL", interest.toPlainString());
        return toResponse(account);
    }

    private BigDecimal computeDailyInterest(Account account) {
        if (account.getProduct() == null) return BigDecimal.ZERO;
        BigDecimal rate = account.getProduct().getInterestRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        if (account.getBalance().compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return account.getBalance()
            .multiply(rate)
            .divide(BigDecimal.valueOf(100L * 365), 4, RoundingMode.HALF_UP);
    }

    private String generateReference() {
        return "TXN-" + System.currentTimeMillis();
    }

    private Account findById(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Account", id));
    }

    AccountResponse toResponse(Account a) {
        BigDecimal onHold = accountHoldRepository.sumActiveHoldsByAccount(a.getId());
        BigDecimal available = a.getBalance().subtract(onHold);
        String customerName = a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName();
        var p = a.getProduct();
        return new AccountResponse(
            a.getId(), a.getAccountNumber(), a.getCustomer().getId(),
            customerName, p.getName(),
            a.getAccountType(), a.getStatus(), a.getBalance(),
            available, onHold,
            a.getCurrencyCode(), a.getOpenedDate(), a.getClosedDate(),
            a.getLastTransactionDate(), a.getCreatedAt(),
            p.isAllowOverdraft(), p.getOverdraftLimit(), p.getMinimumBalance()
        );
    }

    private AccountHoldResponse toHoldResponse(AccountHold h) {
        return new AccountHoldResponse(
            h.getId(), h.getAccount().getId(),
            h.getAmount(), h.getReason(), h.getReferenceNumber(),
            h.getStatus(), h.getExpiryDate(),
            h.getReleasedAt(), h.getReleasedBy(),
            h.getCreatedAt(), h.getCreatedBy()
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

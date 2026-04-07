package com.cba.share;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareService {

    public record CreateShareProductRequest(
        String name, String shortName, String description, String currencyCode,
        Long totalShares, BigDecimal unitPrice, Long nominalShares,
        Long minimumShares, Long maximumShares,
        Integer minimumActivePeriodFrequency, String minimumActivePeriodFrequencyType,
        Integer lockInPeriodFrequency, String lockInPeriodFrequencyType,
        boolean allowDividendsForInactive
    ) {}

    public record ApplySharesRequest(
        UUID customerId, UUID productId, Long requestedShares
    ) {}

    public record ShareTransactionRequest(
        Long numberOfShares, BigDecimal unitPrice, LocalDate transactionDate
    ) {}

    private final ShareProductRepository productRepository;
    private final ShareAccountRepository accountRepository;
    private final ShareAccountTransactionRepository transactionRepository;
    private final EntityManager entityManager;
    private final AuditLogService auditLogService;

    // ── Share Products ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ShareProduct> listProducts(Pageable p) {
        return productRepository.findAll(p);
    }

    @Transactional(readOnly = true)
    public ShareProduct getProduct(UUID id) {
        return productRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("ShareProduct", id.toString()));
    }

    @Transactional
    public ShareProduct createProduct(CreateShareProductRequest req) {
        if (productRepository.existsByShortName(req.shortName())) {
            throw CbaException.conflict("SHORT_NAME_EXISTS", "Short name '" + req.shortName() + "' already exists");
        }
        ShareProduct p = new ShareProduct();
        mapProduct(p, req);
        ShareProduct saved = productRepository.save(p);
        auditLogService.log("ShareProduct", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public ShareProduct updateProduct(UUID id, CreateShareProductRequest req) {
        ShareProduct p = getProduct(id);
        if (!p.getShortName().equals(req.shortName()) && productRepository.existsByShortName(req.shortName())) {
            throw CbaException.conflict("SHORT_NAME_EXISTS", "Short name '" + req.shortName() + "' already exists");
        }
        mapProduct(p, req);
        ShareProduct saved = productRepository.save(p);
        auditLogService.log("ShareProduct", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteProduct(UUID id) {
        ShareProduct p = getProduct(id);
        productRepository.delete(p);
        auditLogService.log("ShareProduct", id.toString(), "DELETE", null, null);
    }

    // ── Share Accounts ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ShareAccount> listAccounts(UUID customerId, Pageable p) {
        if (customerId == null) return accountRepository.findAll(p);
        return accountRepository.findByCustomerId(customerId, p);
    }

    @Transactional(readOnly = true)
    public ShareAccount getAccount(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("ShareAccount", id.toString()));
    }

    @Transactional
    public ShareAccount applyForShares(ApplySharesRequest req) {
        Customer customer = entityManager.find(Customer.class, req.customerId());
        if (customer == null) throw CbaException.notFound("Customer", req.customerId().toString());
        ShareProduct product = getProduct(req.productId());

        ShareAccount account = new ShareAccount();
        account.setCustomer(customer);
        account.setProduct(product);
        account.setRequestedShares(req.requestedShares());
        account.setUnitPrice(product.getUnitPrice());
        account.setSubmittedOnDate(LocalDate.now());
        account.setStatus(ShareAccount.Status.SUBMITTED);
        ShareAccount saved = accountRepository.save(account);
        auditLogService.log("ShareAccount", saved.getId().toString(), "SUBMIT", null, saved);
        return saved;
    }

    @Transactional
    public ShareAccount approveAccount(UUID id) {
        ShareAccount account = getAccount(id);
        account.setStatus(ShareAccount.Status.APPROVED);
        account.setApprovedShares(account.getRequestedShares());
        account.setApprovedOnDate(LocalDate.now());
        ShareAccount saved = accountRepository.save(account);
        auditLogService.log("ShareAccount", id.toString(), "APPROVE", null, saved);
        return saved;
    }

    @Transactional
    public ShareAccount activateAccount(UUID id) {
        ShareAccount account = getAccount(id);
        account.setStatus(ShareAccount.Status.ACTIVE);
        account.setActivatedOnDate(LocalDate.now());
        // Issue approved shares to product
        ShareProduct product = account.getProduct();
        if (account.getApprovedShares() != null) {
            product.setSharesIssued(product.getSharesIssued() + account.getApprovedShares());
            account.setTotalSharesHeld(account.getApprovedShares());
            productRepository.save(product);
        }
        ShareAccount saved = accountRepository.save(account);
        auditLogService.log("ShareAccount", id.toString(), "ACTIVATE", null, saved);
        return saved;
    }

    @Transactional
    public ShareAccount rejectAccount(UUID id) {
        ShareAccount account = getAccount(id);
        account.setStatus(ShareAccount.Status.REJECTED);
        ShareAccount saved = accountRepository.save(account);
        auditLogService.log("ShareAccount", id.toString(), "REJECT", null, saved);
        return saved;
    }

    @Transactional
    public ShareAccount closeAccount(UUID id) {
        ShareAccount account = getAccount(id);
        account.setStatus(ShareAccount.Status.CLOSED);
        account.setClosedOnDate(LocalDate.now());
        ShareAccount saved = accountRepository.save(account);
        auditLogService.log("ShareAccount", id.toString(), "CLOSE", null, saved);
        return saved;
    }

    // ── Share Transactions ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ShareAccountTransaction> getTransactions(UUID accountId, Pageable p) {
        getAccount(accountId);
        return transactionRepository.findByShareAccountId(accountId, p);
    }

    @Transactional
    public ShareAccountTransaction purchaseShares(UUID accountId, ShareTransactionRequest req) {
        ShareAccount account = getAccount(accountId);
        ShareAccountTransaction tx = new ShareAccountTransaction();
        tx.setShareAccount(account);
        tx.setTransactionType(ShareAccountTransaction.TransactionType.PURCHASE);
        tx.setNumberOfShares(req.numberOfShares());
        tx.setUnitPrice(req.unitPrice() != null ? req.unitPrice() : account.getUnitPrice());
        tx.setTransactionDate(req.transactionDate() != null ? req.transactionDate() : LocalDate.now());
        ShareAccountTransaction saved = transactionRepository.save(tx);
        account.setTotalSharesHeld(account.getTotalSharesHeld() + req.numberOfShares());
        accountRepository.save(account);
        auditLogService.log("ShareTransaction", saved.getId().toString(), "PURCHASE", null, saved);
        return saved;
    }

    @Transactional
    public ShareAccountTransaction redeemShares(UUID accountId, ShareTransactionRequest req) {
        ShareAccount account = getAccount(accountId);
        if (account.getTotalSharesHeld() < req.numberOfShares()) {
            throw CbaException.badRequest("INSUFFICIENT_SHARES", "Not enough shares to redeem");
        }
        ShareAccountTransaction tx = new ShareAccountTransaction();
        tx.setShareAccount(account);
        tx.setTransactionType(ShareAccountTransaction.TransactionType.REDEEM);
        tx.setNumberOfShares(req.numberOfShares());
        tx.setUnitPrice(req.unitPrice() != null ? req.unitPrice() : account.getUnitPrice());
        tx.setTransactionDate(req.transactionDate() != null ? req.transactionDate() : LocalDate.now());
        ShareAccountTransaction saved = transactionRepository.save(tx);
        account.setTotalSharesHeld(account.getTotalSharesHeld() - req.numberOfShares());
        accountRepository.save(account);
        auditLogService.log("ShareTransaction", saved.getId().toString(), "REDEEM", null, saved);
        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void mapProduct(ShareProduct p, CreateShareProductRequest req) {
        p.setName(req.name());
        p.setShortName(req.shortName());
        p.setDescription(req.description());
        p.setCurrencyCode(req.currencyCode() != null ? req.currencyCode() : "USD");
        p.setTotalShares(req.totalShares());
        p.setUnitPrice(req.unitPrice());
        p.setNominalShares(req.nominalShares());
        p.setMinimumShares(req.minimumShares());
        p.setMaximumShares(req.maximumShares());
        p.setMinimumActivePeriodFrequency(req.minimumActivePeriodFrequency());
        p.setMinimumActivePeriodFrequencyType(req.minimumActivePeriodFrequencyType());
        p.setLockInPeriodFrequency(req.lockInPeriodFrequency());
        p.setLockInPeriodFrequencyType(req.lockInPeriodFrequencyType());
        p.setAllowDividendsForInactive(req.allowDividendsForInactive());
    }
}

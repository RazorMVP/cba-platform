package com.cba.deposit;

import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FixedDepositService {

    public record CreateFdProductRequest(
        String name, String shortName, String description, String currencyCode,
        BigDecimal minDepositAmount, BigDecimal maxDepositAmount, BigDecimal nominalAnnualInterestRate,
        int minDepositTerm, Integer maxDepositTerm, boolean prePenaltyApplicable, BigDecimal prePenaltyInterest
    ) {}

    public record SubmitFdRequest(
        UUID customerId, UUID productId, BigDecimal depositAmount,
        int depositPeriod, String depositPeriodType, LocalDate expectedFirstDepositOnDate
    ) {}

    private final FixedDepositProductRepository productRepository;
    private final FixedDepositAccountRepository accountRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<FixedDepositProduct> listProducts(Pageable p) {
        return productRepository.findAll(p);
    }

    @Transactional(readOnly = true)
    public FixedDepositProduct getProduct(UUID id) {
        return productRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("FixedDepositProduct", id.toString()));
    }

    @Transactional
    public FixedDepositProduct createProduct(CreateFdProductRequest req) {
        FixedDepositProduct p = new FixedDepositProduct();
        mapProduct(p, req);
        return productRepository.save(p);
    }

    @Transactional
    public FixedDepositProduct updateProduct(UUID id, CreateFdProductRequest req) {
        FixedDepositProduct p = getProduct(id);
        mapProduct(p, req);
        return productRepository.save(p);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        FixedDepositProduct p = getProduct(id);
        productRepository.delete(p);
    }

    private void mapProduct(FixedDepositProduct p, CreateFdProductRequest req) {
        p.setName(req.name());
        p.setShortName(req.shortName());
        p.setDescription(req.description());
        p.setCurrencyCode(req.currencyCode());
        p.setMinDepositAmount(req.minDepositAmount());
        p.setMaxDepositAmount(req.maxDepositAmount());
        p.setNominalAnnualInterestRate(req.nominalAnnualInterestRate());
        p.setMinDepositTerm(req.minDepositTerm());
        p.setMaxDepositTerm(req.maxDepositTerm());
        p.setPrePenaltyApplicable(req.prePenaltyApplicable());
        p.setPrePenaltyInterest(req.prePenaltyInterest());
    }

    @Transactional(readOnly = true)
    public Page<FixedDepositAccount> listAccounts(UUID customerId, Pageable p) {
        if (customerId == null) return accountRepository.findAll(p);
        return accountRepository.findByCustomerId(customerId, p);
    }

    @Transactional(readOnly = true)
    public FixedDepositAccount getAccount(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("FixedDepositAccount", id.toString()));
    }

    @Transactional
    public FixedDepositAccount submitApplication(SubmitFdRequest req) {
        Customer customer = entityManager.find(Customer.class, req.customerId());
        if (customer == null) throw CbaException.notFound("Customer", req.customerId().toString());
        FixedDepositProduct product = getProduct(req.productId());
        FixedDepositAccount account = new FixedDepositAccount();
        account.setCustomer(customer);
        account.setProduct(product);
        account.setDepositAmount(req.depositAmount());
        account.setDepositPeriod(req.depositPeriod());
        account.setDepositPeriodType(req.depositPeriodType());
        account.setExpectedFirstDepositOnDate(req.expectedFirstDepositOnDate());
        account.setSubmittedOnDate(LocalDate.now());
        account.setNominalAnnualInterestRate(product.getNominalAnnualInterestRate());
        account.setStatus(FixedDepositAccount.Status.SUBMITTED);
        return accountRepository.save(account);
    }

    @Transactional
    public FixedDepositAccount approveAccount(UUID id) {
        FixedDepositAccount account = getAccount(id);
        account.setStatus(FixedDepositAccount.Status.APPROVED);
        account.setApprovedOnDate(LocalDate.now());
        return accountRepository.save(account);
    }

    @Transactional
    public FixedDepositAccount activateAccount(UUID id) {
        FixedDepositAccount account = getAccount(id);
        account.setStatus(FixedDepositAccount.Status.ACTIVE);
        account.setActivatedOnDate(LocalDate.now());
        return accountRepository.save(account);
    }

    @Transactional
    public FixedDepositAccount rejectAccount(UUID id) {
        FixedDepositAccount account = getAccount(id);
        account.setStatus(FixedDepositAccount.Status.REJECTED);
        return accountRepository.save(account);
    }

    @Transactional
    public FixedDepositAccount prematureClose(UUID id) {
        FixedDepositAccount account = getAccount(id);
        FixedDepositProduct product = account.getProduct();
        if (product.isPrePenaltyApplicable() && product.getPrePenaltyInterest() != null) {
            BigDecimal penalty = account.getDepositAmount()
                .multiply(product.getPrePenaltyInterest())
                .divide(BigDecimal.valueOf(100));
            BigDecimal adjusted = account.getDepositAmount().subtract(penalty);
            account.setMaturityAmount(adjusted.compareTo(BigDecimal.ZERO) > 0 ? adjusted : BigDecimal.ZERO);
        }
        account.setStatus(FixedDepositAccount.Status.PREMATURE_CLOSURE);
        account.setClosedOnDate(LocalDate.now());
        return accountRepository.save(account);
    }

    @Transactional
    public FixedDepositAccount matureAccount(UUID id) {
        FixedDepositAccount account = getAccount(id);
        account.setStatus(FixedDepositAccount.Status.MATURED);
        account.setMaturityDate(LocalDate.now());
        return accountRepository.save(account);
    }
}

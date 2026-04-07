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
public class RecurringDepositService {

    public record CreateRdProductRequest(
        String name, String shortName, String description, String currencyCode,
        BigDecimal mandatoryRecommendedDepositAmount,
        BigDecimal minDepositAmount, BigDecimal maxDepositAmount, BigDecimal nominalAnnualInterestRate,
        int minDepositTerm, Integer maxDepositTerm, boolean prePenaltyApplicable, BigDecimal prePenaltyInterest
    ) {}

    public record SubmitRdRequest(
        UUID customerId, UUID productId, BigDecimal mandatoryRecommendedDepositAmount,
        int depositPeriod, String depositPeriodType, LocalDate expectedFirstDepositOnDate
    ) {}

    private final RecurringDepositProductRepository productRepository;
    private final RecurringDepositAccountRepository accountRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<RecurringDepositProduct> listProducts(Pageable p) {
        return productRepository.findAll(p);
    }

    @Transactional(readOnly = true)
    public RecurringDepositProduct getProduct(UUID id) {
        return productRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("RecurringDepositProduct", id.toString()));
    }

    @Transactional
    public RecurringDepositProduct createProduct(CreateRdProductRequest req) {
        RecurringDepositProduct p = new RecurringDepositProduct();
        mapProduct(p, req);
        return productRepository.save(p);
    }

    @Transactional
    public RecurringDepositProduct updateProduct(UUID id, CreateRdProductRequest req) {
        RecurringDepositProduct p = getProduct(id);
        mapProduct(p, req);
        return productRepository.save(p);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        RecurringDepositProduct p = getProduct(id);
        productRepository.delete(p);
    }

    private void mapProduct(RecurringDepositProduct p, CreateRdProductRequest req) {
        p.setName(req.name());
        p.setShortName(req.shortName());
        p.setDescription(req.description());
        p.setCurrencyCode(req.currencyCode());
        p.setMandatoryRecommendedDepositAmount(req.mandatoryRecommendedDepositAmount());
        p.setMinDepositAmount(req.minDepositAmount());
        p.setMaxDepositAmount(req.maxDepositAmount());
        p.setNominalAnnualInterestRate(req.nominalAnnualInterestRate());
        p.setMinDepositTerm(req.minDepositTerm());
        p.setMaxDepositTerm(req.maxDepositTerm());
        p.setPrePenaltyApplicable(req.prePenaltyApplicable());
        p.setPrePenaltyInterest(req.prePenaltyInterest());
    }

    @Transactional(readOnly = true)
    public Page<RecurringDepositAccount> listAccounts(UUID customerId, Pageable p) {
        if (customerId == null) return accountRepository.findAll(p);
        return accountRepository.findByCustomerId(customerId, p);
    }

    @Transactional(readOnly = true)
    public RecurringDepositAccount getAccount(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("RecurringDepositAccount", id.toString()));
    }

    @Transactional
    public RecurringDepositAccount submitApplication(SubmitRdRequest req) {
        Customer customer = entityManager.find(Customer.class, req.customerId());
        if (customer == null) throw CbaException.notFound("Customer", req.customerId().toString());
        RecurringDepositProduct product = getProduct(req.productId());
        RecurringDepositAccount account = new RecurringDepositAccount();
        account.setCustomer(customer);
        account.setProduct(product);
        account.setMandatoryRecommendedDepositAmount(req.mandatoryRecommendedDepositAmount());
        account.setDepositPeriod(req.depositPeriod());
        account.setDepositPeriodType(req.depositPeriodType());
        account.setExpectedFirstDepositOnDate(req.expectedFirstDepositOnDate());
        account.setSubmittedOnDate(LocalDate.now());
        account.setNominalAnnualInterestRate(product.getNominalAnnualInterestRate());
        account.setStatus(RecurringDepositAccount.Status.SUBMITTED);
        return accountRepository.save(account);
    }

    @Transactional
    public RecurringDepositAccount approveAccount(UUID id) {
        RecurringDepositAccount account = getAccount(id);
        account.setStatus(RecurringDepositAccount.Status.APPROVED);
        account.setApprovedOnDate(LocalDate.now());
        return accountRepository.save(account);
    }

    @Transactional
    public RecurringDepositAccount activateAccount(UUID id) {
        RecurringDepositAccount account = getAccount(id);
        account.setStatus(RecurringDepositAccount.Status.ACTIVE);
        account.setActivatedOnDate(LocalDate.now());
        return accountRepository.save(account);
    }

    @Transactional
    public RecurringDepositAccount rejectAccount(UUID id) {
        RecurringDepositAccount account = getAccount(id);
        account.setStatus(RecurringDepositAccount.Status.REJECTED);
        return accountRepository.save(account);
    }

    @Transactional
    public RecurringDepositAccount prematureClose(UUID id) {
        RecurringDepositAccount account = getAccount(id);
        RecurringDepositProduct product = account.getProduct();
        if (product.isPrePenaltyApplicable() && product.getPrePenaltyInterest() != null
                && account.getDepositAmount() != null) {
            BigDecimal penalty = account.getDepositAmount()
                .multiply(product.getPrePenaltyInterest())
                .divide(BigDecimal.valueOf(100));
            BigDecimal adjusted = account.getDepositAmount().subtract(penalty);
            account.setMaturityAmount(adjusted.compareTo(BigDecimal.ZERO) > 0 ? adjusted : BigDecimal.ZERO);
        }
        account.setStatus(RecurringDepositAccount.Status.PREMATURE_CLOSURE);
        account.setClosedOnDate(LocalDate.now());
        return accountRepository.save(account);
    }

    @Transactional
    public RecurringDepositAccount matureAccount(UUID id) {
        RecurringDepositAccount account = getAccount(id);
        account.setStatus(RecurringDepositAccount.Status.MATURED);
        account.setMaturityDate(LocalDate.now());
        return accountRepository.save(account);
    }
}

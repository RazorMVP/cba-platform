package com.cba.product;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.product.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final LoanProductRepository loanProductRepository;
    private final DepositProductRepository depositProductRepository;
    private final AuditLogService auditLogService;

    // ── Loan Products ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LoanProductResponse> getAllLoanProducts(boolean activeOnly) {
        List<LoanProduct> products = activeOnly
                ? loanProductRepository.findByActiveTrue()
                : loanProductRepository.findAll();
        return products.stream().map(LoanProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LoanProductResponse getLoanProduct(UUID id) {
        return LoanProductResponse.from(findLoanProductById(id));
    }

    @Transactional
    public LoanProductResponse createLoanProduct(LoanProductRequest request) {
        validateLoanProductRanges(request);

        LoanProduct product = new LoanProduct();
        applyLoanProductFields(product, request);

        LoanProduct saved = loanProductRepository.save(product);
        auditLogService.log("LoanProduct", saved.getId().toString(), "CREATE", null, saved);
        return LoanProductResponse.from(saved);
    }

    @Transactional
    public LoanProductResponse updateLoanProduct(UUID id, LoanProductRequest request) {
        validateLoanProductRanges(request);
        LoanProduct product = findLoanProductById(id);
        applyLoanProductFields(product, request);

        LoanProduct saved = loanProductRepository.save(product);
        auditLogService.log("LoanProduct", id.toString(), "UPDATE", null, saved);
        return LoanProductResponse.from(saved);
    }

    @Transactional
    public void deactivateLoanProduct(UUID id) {
        LoanProduct product = findLoanProductById(id);
        product.setActive(false);
        loanProductRepository.save(product);
        auditLogService.log("LoanProduct", id.toString(), "DEACTIVATE", null, null);
    }

    // ── Deposit Products ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DepositProductResponse> getAllDepositProducts(boolean activeOnly) {
        List<DepositProduct> products = activeOnly
                ? depositProductRepository.findByActiveTrue()
                : depositProductRepository.findAll();
        return products.stream().map(DepositProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DepositProductResponse getDepositProduct(UUID id) {
        return DepositProductResponse.from(findDepositProductById(id));
    }

    @Transactional
    public DepositProductResponse createDepositProduct(DepositProductRequest request) {
        DepositProduct product = new DepositProduct();
        applyDepositProductFields(product, request);

        DepositProduct saved = depositProductRepository.save(product);
        auditLogService.log("DepositProduct", saved.getId().toString(), "CREATE", null, saved);
        return DepositProductResponse.from(saved);
    }

    @Transactional
    public DepositProductResponse updateDepositProduct(UUID id, DepositProductRequest request) {
        DepositProduct product = findDepositProductById(id);
        applyDepositProductFields(product, request);

        DepositProduct saved = depositProductRepository.save(product);
        auditLogService.log("DepositProduct", id.toString(), "UPDATE", null, saved);
        return DepositProductResponse.from(saved);
    }

    @Transactional
    public void deactivateDepositProduct(UUID id) {
        DepositProduct product = findDepositProductById(id);
        product.setActive(false);
        depositProductRepository.save(product);
        auditLogService.log("DepositProduct", id.toString(), "DEACTIVATE", null, null);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private LoanProduct findLoanProductById(UUID id) {
        return loanProductRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("LoanProduct", id));
    }

    private DepositProduct findDepositProductById(UUID id) {
        return depositProductRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("DepositProduct", id));
    }

    private void applyLoanProductFields(LoanProduct product, LoanProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCurrencyCode(request.currencyCode() != null ? request.currencyCode().toUpperCase() : "USD");
        product.setMinPrincipal(request.minPrincipal());
        product.setMaxPrincipal(request.maxPrincipal());
        product.setMinInterestRate(request.minInterestRate());
        product.setMaxInterestRate(request.maxInterestRate());
        product.setDefaultInterestRate(request.defaultInterestRate());
        product.setMinTermMonths(request.minTermMonths());
        product.setMaxTermMonths(request.maxTermMonths());
        if (request.repaymentType() != null) product.setRepaymentType(request.repaymentType());
        product.setOriginationFee(request.originationFee() != null ? request.originationFee() : BigDecimal.ZERO);
        product.setLatePaymentFee(request.latePaymentFee() != null ? request.latePaymentFee() : BigDecimal.ZERO);
    }

    private void applyDepositProductFields(DepositProduct product, DepositProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setAccountType(request.accountType());
        product.setCurrencyCode(request.currencyCode() != null ? request.currencyCode().toUpperCase() : "USD");
        product.setMinimumBalance(request.minimumBalance() != null ? request.minimumBalance() : BigDecimal.ZERO);
        product.setInterestRate(request.interestRate() != null ? request.interestRate() : BigDecimal.ZERO);
        if (request.interestCompounding() != null) product.setInterestCompounding(request.interestCompounding());
    }

    private void validateLoanProductRanges(LoanProductRequest request) {
        if (request.minPrincipal().compareTo(request.maxPrincipal()) > 0) {
            throw CbaException.badRequest("INVALID_RANGE", "minPrincipal must not exceed maxPrincipal");
        }
        if (request.minInterestRate().compareTo(request.maxInterestRate()) > 0) {
            throw CbaException.badRequest("INVALID_RANGE", "minInterestRate must not exceed maxInterestRate");
        }
        if (request.defaultInterestRate().compareTo(request.minInterestRate()) < 0
                || request.defaultInterestRate().compareTo(request.maxInterestRate()) > 0) {
            throw CbaException.badRequest("INVALID_RANGE",
                    "defaultInterestRate must be within [minInterestRate, maxInterestRate]");
        }
        if (request.minTermMonths() > request.maxTermMonths()) {
            throw CbaException.badRequest("INVALID_RANGE", "minTermMonths must not exceed maxTermMonths");
        }
    }
}

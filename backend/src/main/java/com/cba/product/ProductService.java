package com.cba.product;

import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountRepository;
import com.cba.audit.AuditLogService;
import com.cba.charge.ChargeDefinition;
import com.cba.charge.ChargeDefinitionRepository;
import com.cba.common.exception.CbaException;
import com.cba.product.dto.*;
import com.cba.system.Fund;
import com.cba.system.FundRepository;
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
    private final GlAccountRepository glAccountRepository;
    private final ChargeDefinitionRepository chargeDefinitionRepository;
    private final FundRepository fundRepository;
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

    private void applyLoanProductFields(LoanProduct p, LoanProductRequest r) {
        p.setName(r.name());
        p.setShortName(r.shortName().toUpperCase());
        p.setDescription(r.description());
        p.setCurrencyCode(r.currencyCode() != null ? r.currencyCode().toUpperCase() : "USD");

        // Fund linkage
        p.setFund(r.fundId() != null ? requireFund(r.fundId()) : null);

        // Principal
        p.setMinPrincipal(r.minPrincipal());
        p.setMaxPrincipal(r.maxPrincipal());
        p.setDefaultPrincipal(r.defaultPrincipal());
        p.setInstallmentAmountInMultiplesOf(r.installmentAmountInMultiplesOf());

        // Interest rates
        p.setMinInterestRate(r.minInterestRate());
        p.setMaxInterestRate(r.maxInterestRate());
        p.setDefaultInterestRate(r.defaultInterestRate());
        if (r.interestRateFrequencyType() != null) p.setInterestRateFrequencyType(r.interestRateFrequencyType());
        if (r.interestType() != null)               p.setInterestType(r.interestType());
        if (r.amortizationType() != null)           p.setAmortizationType(r.amortizationType());
        if (r.interestCalculationPeriodType() != null) p.setInterestCalculationPeriodType(r.interestCalculationPeriodType());
        if (r.daysInYearType() != null)             p.setDaysInYearType(r.daysInYearType());
        if (r.daysInMonthType() != null)            p.setDaysInMonthType(r.daysInMonthType());

        // Repayment schedule
        p.setMinTermMonths(r.minTermMonths());
        p.setMaxTermMonths(r.maxTermMonths());
        if (r.numberOfRepayments() != null) p.setNumberOfRepayments(r.numberOfRepayments());
        if (r.repaymentEvery() != null)     p.setRepaymentEvery(r.repaymentEvery());
        if (r.repaymentFrequencyType() != null) p.setRepaymentFrequencyType(r.repaymentFrequencyType());
        if (r.repaymentType() != null)      p.setRepaymentType(r.repaymentType());

        // Grace periods
        p.setGraceOnPrincipalPayment(r.graceOnPrincipalPayment());
        p.setGraceOnInterestPayment(r.graceOnInterestPayment());
        p.setGraceOnInterestCharged(r.graceOnInterestCharged());
        p.setGraceOnArrearsAgeing(r.graceOnArrearsAgeing());
        p.setInArrearsTolerance(r.inArrearsTolerance());

        // Fees
        p.setOriginationFee(r.originationFee() != null ? r.originationFee() : BigDecimal.ZERO);
        p.setLatePaymentFee(r.latePaymentFee() != null ? r.latePaymentFee() : BigDecimal.ZERO);

        // Attribute overrides
        if (r.allowAttributeOverrides() != null) {
            AllowAttributeOverrides o = p.getAllowAttributeOverrides() != null
                    ? p.getAllowAttributeOverrides() : new AllowAttributeOverrides();
            var req = r.allowAttributeOverrides();
            if (req.amortizationType() != null)                   o.setAmortizationType(req.amortizationType());
            if (req.interestType() != null)                        o.setInterestType(req.interestType());
            if (req.repaymentEvery() != null)                      o.setRepaymentEvery(req.repaymentEvery());
            if (req.repaymentFrequency() != null)                  o.setRepaymentFrequency(req.repaymentFrequency());
            if (req.repaymentStrategy() != null)                   o.setRepaymentStrategy(req.repaymentStrategy());
            if (req.graceOnPrincipalAndInterestPayment() != null)  o.setGraceOnPrincipalAndInterestPayment(req.graceOnPrincipalAndInterestPayment());
            if (req.graceOnInterestCharged() != null)              o.setGraceOnInterestCharged(req.graceOnInterestCharged());
            if (req.interestRatePerPeriod() != null)               o.setInterestRatePerPeriod(req.interestRatePerPeriod());
            p.setAllowAttributeOverrides(o);
        }

        // GL accounts
        p.setFundSourceAccount(resolveGl(r.fundSourceAccountId()));
        p.setLoanPortfolioAccount(resolveGl(r.loanPortfolioAccountId()));
        p.setTransfersInSuspenseAccount(resolveGl(r.transfersInSuspenseAccountId()));
        p.setInterestOnLoanAccount(resolveGl(r.interestOnLoanAccountId()));
        p.setIncomeFromFeesAccount(resolveGl(r.incomeFromFeesAccountId()));
        p.setIncomeFromPenaltiesAccount(resolveGl(r.incomeFromPenaltiesAccountId()));
        p.setWriteOffAccount(resolveGl(r.writeOffAccountId()));
        p.setOverpaymentLiabilityAccount(resolveGl(r.overpaymentLiabilityAccountId()));

        // Charges (replace-all)
        p.getCharges().clear();
        if (r.chargeIds() != null && !r.chargeIds().isEmpty()) {
            List<ChargeDefinition> charges = chargeDefinitionRepository.findAllById(r.chargeIds());
            p.getCharges().addAll(charges);
        }
    }

    private void applyDepositProductFields(DepositProduct p, DepositProductRequest r) {
        p.setName(r.name());
        p.setShortName(r.shortName().toUpperCase());
        p.setDescription(r.description());
        p.setAccountType(r.accountType());
        p.setCurrencyCode(r.currencyCode() != null ? r.currencyCode().toUpperCase() : "USD");

        // Balance
        p.setMinimumBalance(r.minimumBalance() != null ? r.minimumBalance() : BigDecimal.ZERO);
        p.setMinRequiredOpeningBalance(r.minRequiredOpeningBalance());

        // Interest
        p.setInterestRate(r.interestRate() != null ? r.interestRate() : BigDecimal.ZERO);
        if (r.interestCompounding() != null)         p.setInterestCompounding(r.interestCompounding());
        if (r.interestPostingPeriodType() != null)   p.setInterestPostingPeriodType(r.interestPostingPeriodType());
        if (r.daysInYearType() != null)              p.setDaysInYearType(r.daysInYearType());
        if (r.daysInMonthType() != null)             p.setDaysInMonthType(r.daysInMonthType());

        // Lock-in
        p.setLockinPeriodFrequency(r.lockinPeriodFrequency());
        p.setLockinPeriodFrequencyType(r.lockinPeriodFrequencyType());

        // Withdrawal
        if (r.withdrawalFeeForTransfers() != null) p.setWithdrawalFeeForTransfers(r.withdrawalFeeForTransfers());

        // Overdraft
        if (r.allowOverdraft() != null) p.setAllowOverdraft(r.allowOverdraft());
        p.setOverdraftLimit(r.overdraftLimit());
        p.setNominalAnnualInterestRateOverdraft(r.nominalAnnualInterestRateOverdraft());
        p.setMinOverdraftForInterestCalculation(r.minOverdraftForInterestCalculation());

        // Accounting
        if (r.accountingType() != null) p.setAccountingType(r.accountingType());

        // GL accounts
        p.setSavingsReferenceAccount(resolveGl(r.savingsReferenceAccountId()));
        p.setSavingsControlAccount(resolveGl(r.savingsControlAccountId()));
        p.setTransfersInSuspenseAccount(resolveGl(r.transfersInSuspenseAccountId()));
        p.setInterestOnSavingsAccount(resolveGl(r.interestOnSavingsAccountId()));
        p.setIncomeFromFeesAccount(resolveGl(r.incomeFromFeesAccountId()));
        p.setIncomeFromPenaltiesAccount(resolveGl(r.incomeFromPenaltiesAccountId()));
        p.setWriteOffAccount(resolveGl(r.writeOffAccountId()));
        p.setOverdraftPortfolioControlAccount(resolveGl(r.overdraftPortfolioControlAccountId()));

        // Charges (replace-all)
        p.getCharges().clear();
        if (r.chargeIds() != null && !r.chargeIds().isEmpty()) {
            List<ChargeDefinition> charges = chargeDefinitionRepository.findAllById(r.chargeIds());
            p.getCharges().addAll(charges);
        }
    }

    private void validateLoanProductRanges(LoanProductRequest r) {
        if (r.minPrincipal().compareTo(r.maxPrincipal()) > 0)
            throw CbaException.badRequest("INVALID_RANGE", "minPrincipal must not exceed maxPrincipal");
        if (r.minInterestRate().compareTo(r.maxInterestRate()) > 0)
            throw CbaException.badRequest("INVALID_RANGE", "minInterestRate must not exceed maxInterestRate");
        if (r.defaultInterestRate().compareTo(r.minInterestRate()) < 0
                || r.defaultInterestRate().compareTo(r.maxInterestRate()) > 0)
            throw CbaException.badRequest("INVALID_RANGE", "defaultInterestRate must be within [minInterestRate, maxInterestRate]");
        if (r.minTermMonths() > r.maxTermMonths())
            throw CbaException.badRequest("INVALID_RANGE", "minTermMonths must not exceed maxTermMonths");
    }

    private GlAccount resolveGl(UUID id) {
        if (id == null) return null;
        return glAccountRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("GlAccount", id));
    }

    private Fund requireFund(UUID id) {
        return fundRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("Fund", id));
    }
}

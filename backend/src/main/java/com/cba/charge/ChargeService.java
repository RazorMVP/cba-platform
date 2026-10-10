package com.cba.charge;

import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountRepository;
import com.cba.loan.Loan;
import com.cba.loan.LoanGlPosting;
import com.cba.loan.LoanPaymentSource;
import com.cba.loan.LoanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeRepository chargeRepository;
    private final LoanChargeRepository loanChargeRepository;
    private final ClientChargeRepository clientChargeRepository;
    private final EntityManager entityManager;
    private final LoanGlPosting loanGlPosting;
    private final GlAccountRepository glAccountRepository;

    /** {@code incomeAccountId}: optional INCOME GL account for this charge's revenue. */
    public record CreateChargeRequest(
        String name,
        String currencyCode,
        ChargeDefinition.ChargeAppliesTo chargeAppliesTo,
        ChargeDefinition.ChargeTimeType chargeTimeType,
        ChargeDefinition.ChargeCalculation chargeCalculation,
        java.math.BigDecimal amount,
        boolean penalty,
        boolean active,
        UUID incomeAccountId
    ) {
        public CreateChargeRequest(String name, String currencyCode, ChargeDefinition.ChargeAppliesTo chargeAppliesTo,
                                   ChargeDefinition.ChargeTimeType chargeTimeType,
                                   ChargeDefinition.ChargeCalculation chargeCalculation,
                                   java.math.BigDecimal amount, boolean penalty, boolean active) {
            this(name, currencyCode, chargeAppliesTo, chargeTimeType, chargeCalculation, amount, penalty, active, null);
        }
    }

    public record AddChargeRequest(UUID chargeDefinitionId, java.math.BigDecimal amount, java.time.LocalDate dueDate) {}

    @Transactional(readOnly = true)
    public Page<ChargeDefinition> listCharges(ChargeDefinition.ChargeAppliesTo appliesTo, Pageable pageable) {
        if (appliesTo == null) {
            return chargeRepository.findAll(pageable);
        }
        return chargeRepository.findByChargeAppliesTo(appliesTo, pageable);
    }

    @Transactional(readOnly = true)
    public ChargeDefinition getCharge(UUID id) {
        return chargeRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("ChargeDefinition", id.toString()));
    }

    @Transactional
    public ChargeDefinition createCharge(CreateChargeRequest req) {
        ChargeDefinition charge = new ChargeDefinition();
        charge.setName(req.name());
        charge.setCurrencyCode(req.currencyCode());
        charge.setChargeAppliesTo(req.chargeAppliesTo());
        charge.setChargeTimeType(req.chargeTimeType());
        charge.setChargeCalculation(req.chargeCalculation());
        charge.setAmount(req.amount());
        charge.setPenalty(req.penalty());
        charge.setActive(req.active());
        charge.setIncomeAccountId(validIncomeAccount(req.incomeAccountId()));
        return chargeRepository.save(charge);
    }

    /** A charge's own income account must be an enabled INCOME detail account. */
    private UUID validIncomeAccount(UUID id) {
        if (id == null) return null;
        GlAccount gl = glAccountRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("GlAccount", id.toString()));
        if (gl.getAccountType() != GlAccount.AccountType.INCOME || gl.getUsage() != GlAccount.Usage.DETAIL
                || gl.isDisabled()) {
            throw CbaException.badRequest("INVALID_INCOME_ACCOUNT",
                "GL " + gl.getGlCode() + " must be an enabled INCOME detail account");
        }
        return id;
    }

    @Transactional
    public ChargeDefinition updateCharge(UUID id, CreateChargeRequest req) {
        ChargeDefinition charge = getCharge(id);
        charge.setName(req.name());
        charge.setCurrencyCode(req.currencyCode());
        charge.setChargeAppliesTo(req.chargeAppliesTo());
        charge.setChargeTimeType(req.chargeTimeType());
        charge.setChargeCalculation(req.chargeCalculation());
        charge.setAmount(req.amount());
        charge.setPenalty(req.penalty());
        charge.setActive(req.active());
        charge.setIncomeAccountId(validIncomeAccount(req.incomeAccountId()));
        return chargeRepository.save(charge);
    }

    @Transactional
    public void deleteCharge(UUID id) {
        ChargeDefinition charge = getCharge(id);
        chargeRepository.delete(charge);
    }

    @Transactional(readOnly = true)
    public Page<LoanCharge> getLoanCharges(UUID loanId, Pageable pageable) {
        return loanChargeRepository.findByLoanId(loanId, pageable);
    }

    /**
     * Applies a charge to a loan. A penalty is income when charged; a fee when due
     * (IFRS 15 §31). A charge that is income now is posted at once (DR fees receivable /
     * CR fee or penalty income); a fee due later stays unrecognised until its due date.
     */
    @Transactional
    public LoanCharge addLoanCharge(UUID loanId, AddChargeRequest req) {
        Loan loan = entityManager.find(Loan.class, loanId);
        if (loan == null) throw CbaException.notFound("Loan", loanId.toString());
        if (!OPEN_LOAN.contains(loan.getStatus()) && !PRE_DISBURSEMENT.contains(loan.getStatus())) {
            throw CbaException.badRequest("LOAN_NOT_OPEN", "Charges cannot be added to a " + loan.getStatus() + " loan");
        }
        ChargeDefinition def = getCharge(req.chargeDefinitionId());
        if (def.getChargeTimeType() == ChargeDefinition.ChargeTimeType.DISBURSEMENT) {
            // An origination fee is part of the effective interest rate (IFRS 9 B5.4.2):
            // it is spread over the loan's life, never income on the day it is charged.
            throw CbaException.badRequest("ORIGINATION_FEE_NOT_SUPPORTED",
                "Disbursement fees are recognised through the effective interest rate, which is not built yet");
        }
        String loanCurrency = LoanGlPosting.currency(loan);
        if (!loanCurrency.equalsIgnoreCase(def.getCurrencyCode())) {
            throw CbaException.badRequest("CHARGE_CURRENCY_MISMATCH",
                "Charge " + def.getName() + " is in " + def.getCurrencyCode() + "; the loan is in " + loanCurrency);
        }
        BigDecimal amount = req.amount() != null ? req.amount()
            : def.getChargeCalculation() == ChargeDefinition.ChargeCalculation.FLAT ? def.getAmount() : null;
        if (amount == null || amount.signum() <= 0) {
            throw CbaException.badRequest("CHARGE_AMOUNT_REQUIRED", "A positive charge amount is required");
        }

        LoanCharge lc = new LoanCharge();
        lc.setLoan(loan);
        lc.setChargeDefinition(def);
        lc.setName(def.getName());
        lc.setCurrencyCode(def.getCurrencyCode());
        lc.setChargeTimeType(def.getChargeTimeType());
        lc.setChargeCalculation(def.getChargeCalculation());
        lc.setAmount(amount);
        lc.setAmountOutstanding(amount);
        lc.setPenalty(def.isPenalty());
        lc.setDueForCollectionAsOfDate(req.dueDate());

        LocalDate today = LocalDate.now();
        boolean incomeNow = OPEN_LOAN.contains(loan.getStatus())
            && (lc.isPenalty() || req.dueDate() == null || !req.dueDate().isAfter(today));
        if (incomeNow) {
            loanGlPosting.postChargeRecognition(loan, lc, amount, today);
            lc.setIncomeRecognizedOn(today);
        }
        return loanChargeRepository.save(lc);
    }

    /** What pays a loan charge: the borrower's account (default) or teller cash. */
    public record PayChargeRequest(String paymentMethod, UUID sourceAccountId, UUID tellerSessionId) {}

    /** Collects the outstanding amount: DR the money source / CR fees receivable. */
    @Transactional
    public LoanCharge payLoanCharge(UUID loanId, UUID chargeId, PayChargeRequest req) {
        LoanCharge lc = findLoanCharge(loanId, chargeId);
        if (lc.isPaid() || lc.isWaived() || lc.getAmountOutstanding().signum() <= 0) {
            throw CbaException.badRequest("CHARGE_SETTLED", "Charge " + lc.getName() + " has nothing outstanding");
        }
        if (lc.getIncomeRecognizedOn() == null) {
            throw CbaException.badRequest("CHARGE_NOT_DUE",
                "Charge " + lc.getName() + " is due on " + lc.getDueForCollectionAsOfDate() + " and cannot be paid before");
        }
        PayChargeRequest r = req != null ? req : new PayChargeRequest(null, null, null);
        LoanPaymentSource source = LoanPaymentSource.of(r.paymentMethod(), r.sourceAccountId(), r.tellerSessionId());
        BigDecimal amount = lc.getAmountOutstanding();
        loanGlPosting.postChargePayment(lc.getLoan(), lc, source, amount, LocalDate.now());
        lc.setAmountPaid(lc.getAmountPaid().add(amount));
        lc.setAmountOutstanding(BigDecimal.ZERO);
        lc.setPaid(true);
        return loanChargeRepository.save(lc);
    }

    /**
     * Waives what is still outstanding. Income already recognised is reversed (IFRS 15
     * §87-88: the bank gives up consideration it had recognised); a charge not yet income
     * just stops.
     */
    @Transactional
    public LoanCharge waiveLoanCharge(UUID loanId, UUID chargeId) {
        LoanCharge lc = findLoanCharge(loanId, chargeId);
        BigDecimal outstanding = lc.getAmountOutstanding();
        if (lc.isPaid() || lc.isWaived() || outstanding.signum() <= 0) {
            throw CbaException.badRequest("CHARGE_SETTLED", "Charge " + lc.getName() + " has nothing outstanding to waive");
        }
        if (lc.getIncomeRecognizedOn() != null) {
            loanGlPosting.postChargeWaiver(lc.getLoan(), lc, outstanding, LocalDate.now());
        }
        lc.setWaived(true);
        lc.setAmountWaived(lc.getAmountWaived().add(outstanding));
        lc.setAmountOutstanding(BigDecimal.ZERO);
        return loanChargeRepository.save(lc);
    }

    /** Only a charge with nothing posted can be deleted; otherwise waive it. */
    @Transactional
    public void deleteLoanCharge(UUID loanId, UUID chargeId) {
        LoanCharge lc = findLoanCharge(loanId, chargeId);
        if (lc.getIncomeRecognizedOn() != null || lc.getAmountPaid().signum() > 0) {
            throw CbaException.badRequest("CHARGE_HAS_POSTINGS",
                "Charge " + lc.getName() + " is already in the ledger; waive it instead of deleting it");
        }
        loanChargeRepository.delete(lc);
    }

    private LoanCharge findLoanCharge(UUID loanId, UUID chargeId) {
        return loanChargeRepository.findById(chargeId)
            .filter(c -> c.getLoan().getId().equals(loanId))
            .orElseThrow(() -> CbaException.notFound("LoanCharge", chargeId.toString()));
    }

    private static final Set<LoanStatus> OPEN_LOAN = EnumSet.of(LoanStatus.ACTIVE, LoanStatus.IN_ARREARS);
    private static final Set<LoanStatus> PRE_DISBURSEMENT =
        EnumSet.of(LoanStatus.SUBMITTED, LoanStatus.UNDER_REVIEW, LoanStatus.APPROVED);

    @Transactional(readOnly = true)
    public Page<ClientCharge> getClientCharges(UUID customerId, Pageable pageable) {
        return clientChargeRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional
    public ClientCharge addClientCharge(UUID customerId, AddChargeRequest req) {
        Customer customer = entityManager.find(Customer.class, customerId);
        if (customer == null) throw CbaException.notFound("Customer", customerId.toString());
        ChargeDefinition def = getCharge(req.chargeDefinitionId());
        ClientCharge cc = new ClientCharge();
        cc.setCustomer(customer);
        cc.setChargeDefinition(def);
        cc.setName(def.getName());
        cc.setCurrencyCode(def.getCurrencyCode());
        cc.setChargeTimeType(def.getChargeTimeType());
        cc.setChargeCalculation(def.getChargeCalculation());
        cc.setAmount(req.amount());
        cc.setAmountOutstanding(req.amount());
        cc.setPenalty(def.isPenalty());
        cc.setDueDate(req.dueDate());
        return clientChargeRepository.save(cc);
    }

    @Transactional
    public ClientCharge waiveClientCharge(UUID customerId, UUID chargeId) {
        ClientCharge cc = clientChargeRepository.findById(chargeId)
            .filter(c -> c.getCustomer().getId().equals(customerId))
            .orElseThrow(() -> CbaException.notFound("ClientCharge", chargeId.toString()));
        cc.setWaived(true);
        cc.setAmountWaived(cc.getAmount());
        cc.setAmountOutstanding(BigDecimal.ZERO);
        return clientChargeRepository.save(cc);
    }
}

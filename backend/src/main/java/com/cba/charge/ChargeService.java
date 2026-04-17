package com.cba.charge;

import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.loan.Loan;
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
public class ChargeService {

    private final ChargeRepository chargeRepository;
    private final LoanChargeRepository loanChargeRepository;
    private final ClientChargeRepository clientChargeRepository;
    private final EntityManager entityManager;

    public record CreateChargeRequest(
        String name,
        String currencyCode,
        ChargeDefinition.ChargeAppliesTo chargeAppliesTo,
        ChargeDefinition.ChargeTimeType chargeTimeType,
        ChargeDefinition.ChargeCalculation chargeCalculation,
        java.math.BigDecimal amount,
        boolean penalty,
        boolean active
    ) {}

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
        return chargeRepository.save(charge);
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

    @Transactional
    public LoanCharge addLoanCharge(UUID loanId, AddChargeRequest req) {
        Loan loan = entityManager.find(Loan.class, loanId);
        if (loan == null) throw CbaException.notFound("Loan", loanId.toString());
        ChargeDefinition def = getCharge(req.chargeDefinitionId());
        LoanCharge lc = new LoanCharge();
        lc.setLoan(loan);
        lc.setChargeDefinition(def);
        lc.setName(def.getName());
        lc.setCurrencyCode(def.getCurrencyCode());
        lc.setChargeTimeType(def.getChargeTimeType());
        lc.setChargeCalculation(def.getChargeCalculation());
        lc.setAmount(req.amount());
        lc.setAmountOutstanding(req.amount());
        lc.setPenalty(def.isPenalty());
        lc.setDueForCollectionAsOfDate(req.dueDate());
        return loanChargeRepository.save(lc);
    }

    @Transactional
    public LoanCharge payLoanCharge(UUID loanId, UUID chargeId) {
        LoanCharge lc = loanChargeRepository.findById(chargeId)
            .filter(c -> c.getLoan().getId().equals(loanId))
            .orElseThrow(() -> CbaException.notFound("LoanCharge", chargeId.toString()));
        lc.setAmountPaid(lc.getAmount());
        lc.setAmountOutstanding(BigDecimal.ZERO);
        lc.setPaid(true);
        return loanChargeRepository.save(lc);
    }

    @Transactional
    public LoanCharge waiveLoanCharge(UUID loanId, UUID chargeId) {
        LoanCharge lc = loanChargeRepository.findById(chargeId)
            .filter(c -> c.getLoan().getId().equals(loanId))
            .orElseThrow(() -> CbaException.notFound("LoanCharge", chargeId.toString()));
        lc.setWaived(true);
        lc.setAmountWaived(lc.getAmount());
        lc.setAmountOutstanding(BigDecimal.ZERO);
        return loanChargeRepository.save(lc);
    }

    @Transactional
    public void deleteLoanCharge(UUID loanId, UUID chargeId) {
        LoanCharge lc = loanChargeRepository.findById(chargeId)
            .filter(c -> c.getLoan().getId().equals(loanId))
            .orElseThrow(() -> CbaException.notFound("LoanCharge", chargeId.toString()));
        loanChargeRepository.delete(lc);
    }

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

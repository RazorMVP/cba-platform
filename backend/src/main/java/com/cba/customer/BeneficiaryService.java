package com.cba.customer;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    public record CreateBeneficiaryRequest(
        String name,
        String accountNumber,
        String bankNumber,
        BigDecimal transferLimit
    ) {}

    private final BeneficiaryRepository beneficiaryRepository;
    private final AuditLogService       auditLogService;

    @Transactional(readOnly = true)
    public List<Beneficiary> listBeneficiaries(UUID customerId) {
        return beneficiaryRepository.findByCustomerIdAndActiveTrue(customerId);
    }

    @Transactional(readOnly = true)
    public Beneficiary getBeneficiary(UUID customerId, UUID id) {
        Beneficiary b = beneficiaryRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Beneficiary", id));
        if (!customerId.equals(b.getCustomerId())) {
            throw CbaException.notFound("Beneficiary", id);
        }
        return b;
    }

    @Transactional
    public Beneficiary createBeneficiary(UUID customerId, CreateBeneficiaryRequest req) {
        Beneficiary b = new Beneficiary();
        b.setCustomerId(customerId);
        applyRequest(b, req);
        Beneficiary saved = beneficiaryRepository.save(b);
        auditLogService.log("Beneficiary", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public Beneficiary updateBeneficiary(UUID customerId, UUID id, CreateBeneficiaryRequest req) {
        Beneficiary b = getBeneficiary(customerId, id);
        applyRequest(b, req);
        Beneficiary saved = beneficiaryRepository.save(b);
        auditLogService.log("Beneficiary", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deactivate(UUID customerId, UUID id) {
        Beneficiary b = getBeneficiary(customerId, id);
        b.setActive(false);
        beneficiaryRepository.save(b);
        auditLogService.log("Beneficiary", id.toString(), "DEACTIVATE", null, null);
    }

    private void applyRequest(Beneficiary b, CreateBeneficiaryRequest req) {
        b.setName(req.name());
        b.setAccountNumber(req.accountNumber());
        b.setBankNumber(req.bankNumber());
        b.setTransferLimit(req.transferLimit());
    }
}

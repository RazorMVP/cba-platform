package com.cba.accounting;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProvisioningCriteriaService {

    public record DefinitionRequest(
        String categoryName,
        int minAge,
        int maxAge,
        BigDecimal provisionPercentage,
        UUID liabilityAccountId,
        UUID expenseAccountId
    ) {}

    public record CreateCriteriaRequest(
        String criteriaName,
        boolean active,
        List<DefinitionRequest> definitions
    ) {}

    private final ProvisioningCriteriaRepository criteriaRepository;
    private final AuditLogService                auditLogService;

    @Transactional(readOnly = true)
    public Page<ProvisioningCriteria> listCriteria(Pageable p) {
        return criteriaRepository.findAll(p);
    }

    @Transactional(readOnly = true)
    public ProvisioningCriteria getCriteria(UUID id) {
        return criteriaRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("ProvisioningCriteria", id));
    }

    @Transactional
    public ProvisioningCriteria createCriteria(CreateCriteriaRequest req) {
        ProvisioningCriteria pc = new ProvisioningCriteria();
        applyRequest(pc, req);
        ProvisioningCriteria saved = criteriaRepository.save(pc);
        auditLogService.log("ProvisioningCriteria", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public ProvisioningCriteria updateCriteria(UUID id, CreateCriteriaRequest req) {
        ProvisioningCriteria pc = getCriteria(id);
        pc.getDefinitions().clear();
        applyRequest(pc, req);
        ProvisioningCriteria saved = criteriaRepository.save(pc);
        auditLogService.log("ProvisioningCriteria", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteCriteria(UUID id) {
        criteriaRepository.delete(getCriteria(id));
        auditLogService.log("ProvisioningCriteria", id.toString(), "DELETE", null, null);
    }

    private void applyRequest(ProvisioningCriteria pc, CreateCriteriaRequest req) {
        pc.setCriteriaName(req.criteriaName());
        pc.setActive(req.active());
        if (req.definitions() != null) {
            for (DefinitionRequest d : req.definitions()) {
                ProvisioningCriteriaDefinition def = new ProvisioningCriteriaDefinition();
                def.setCriteria(pc);
                def.setCategoryName(d.categoryName());
                def.setMinAge(d.minAge());
                def.setMaxAge(d.maxAge());
                def.setProvisionPercentage(d.provisionPercentage() != null
                    ? d.provisionPercentage() : BigDecimal.ZERO);
                def.setLiabilityAccountId(d.liabilityAccountId());
                def.setExpenseAccountId(d.expenseAccountId());
                pc.getDefinitions().add(def);
            }
        }
    }
}

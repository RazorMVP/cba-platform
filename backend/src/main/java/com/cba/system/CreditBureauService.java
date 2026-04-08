package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditBureauService {

    public record CreateIntegrationRequest(
        String name, String implClass, String creditBureauId, String country
    ) {}

    public record CreateMappingRequest(
        UUID loanProductId, boolean creditCheckMandatory
    ) {}

    private final CreditBureauIntegrationRepository   integrationRepo;
    private final CreditBureauProductMappingRepository mappingRepo;
    private final AuditLogService                      auditLogService;

    // ── Integrations ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CreditBureauIntegration> listIntegrations(Pageable p) {
        return integrationRepo.findAll(p);
    }

    @Transactional(readOnly = true)
    public CreditBureauIntegration getIntegration(UUID id) {
        return integrationRepo.findById(id)
            .orElseThrow(() -> CbaException.notFound("CreditBureauIntegration", id));
    }

    @Transactional
    public CreditBureauIntegration createIntegration(CreateIntegrationRequest req) {
        CreditBureauIntegration cb = new CreditBureauIntegration();
        applyIntegration(cb, req);
        CreditBureauIntegration saved = integrationRepo.save(cb);
        auditLogService.log("CreditBureauIntegration", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public CreditBureauIntegration updateIntegration(UUID id, CreateIntegrationRequest req) {
        CreditBureauIntegration cb = getIntegration(id);
        applyIntegration(cb, req);
        CreditBureauIntegration saved = integrationRepo.save(cb);
        auditLogService.log("CreditBureauIntegration", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public CreditBureauIntegration activate(UUID id) {
        CreditBureauIntegration cb = getIntegration(id);
        cb.setActive(true);
        CreditBureauIntegration saved = integrationRepo.save(cb);
        auditLogService.log("CreditBureauIntegration", id.toString(), "ACTIVATE", null, saved);
        return saved;
    }

    @Transactional
    public CreditBureauIntegration deactivate(UUID id) {
        CreditBureauIntegration cb = getIntegration(id);
        cb.setActive(false);
        CreditBureauIntegration saved = integrationRepo.save(cb);
        auditLogService.log("CreditBureauIntegration", id.toString(), "DEACTIVATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteIntegration(UUID id) {
        integrationRepo.delete(getIntegration(id));
        auditLogService.log("CreditBureauIntegration", id.toString(), "DELETE", null, null);
    }

    // ── Mappings ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CreditBureauProductMapping> listMappings(UUID creditBureauId) {
        return mappingRepo.findByCreditBureauId(creditBureauId);
    }

    @Transactional
    public CreditBureauProductMapping createMapping(UUID creditBureauId, CreateMappingRequest req) {
        CreditBureauIntegration cb = getIntegration(creditBureauId);
        CreditBureauProductMapping m = new CreditBureauProductMapping();
        m.setCreditBureau(cb);
        m.setLoanProductId(req.loanProductId());
        m.setCreditCheckMandatory(req.creditCheckMandatory());
        CreditBureauProductMapping saved = mappingRepo.save(m);
        auditLogService.log("CreditBureauProductMapping", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteMapping(UUID id) {
        CreditBureauProductMapping m = mappingRepo.findById(id)
            .orElseThrow(() -> CbaException.notFound("CreditBureauProductMapping", id));
        mappingRepo.delete(m);
        auditLogService.log("CreditBureauProductMapping", id.toString(), "DELETE", null, null);
    }

    private void applyIntegration(CreditBureauIntegration cb, CreateIntegrationRequest req) {
        cb.setName(req.name());
        cb.setImplClass(req.implClass());
        cb.setCreditBureauId(req.creditBureauId());
        cb.setCountry(req.country());
    }
}

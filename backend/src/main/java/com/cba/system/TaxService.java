package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaxService {

    public record CreateTaxComponentRequest(
        String name,
        BigDecimal percentage,
        String creditAccountType,
        UUID creditAccountId,
        String debitAccountType,
        UUID debitAccountId,
        LocalDate startDate
    ) {}

    public record CreateTaxGroupRequest(
        String name,
        LocalDate startDate,
        List<UUID> taxComponentIds
    ) {}

    private final TaxComponentRepository componentRepository;
    private final TaxGroupRepository groupRepository;
    private final AuditLogService auditLogService;

    // ── Tax Components ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TaxComponent> listComponents(Pageable pageable) {
        return componentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public TaxComponent getComponent(UUID id) {
        return componentRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("TaxComponent", id));
    }

    @Transactional
    public TaxComponent createComponent(CreateTaxComponentRequest req) {
        if (componentRepository.existsByName(req.name())) {
            throw CbaException.conflict("TAX_COMPONENT_NAME_EXISTS",
                "Tax component '" + req.name() + "' already exists");
        }
        TaxComponent tc = new TaxComponent();
        tc.setName(req.name());
        tc.setPercentage(req.percentage());
        tc.setCreditAccountType(req.creditAccountType());
        tc.setCreditAccountId(req.creditAccountId());
        tc.setDebitAccountType(req.debitAccountType());
        tc.setDebitAccountId(req.debitAccountId());
        tc.setStartDate(req.startDate());
        TaxComponent saved = componentRepository.save(tc);
        auditLogService.log("TaxComponent", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public TaxComponent updateComponent(UUID id, CreateTaxComponentRequest req) {
        TaxComponent tc = getComponent(id);
        if (!tc.getName().equals(req.name()) && componentRepository.existsByName(req.name())) {
            throw CbaException.conflict("TAX_COMPONENT_NAME_EXISTS",
                "Tax component '" + req.name() + "' already exists");
        }
        tc.setName(req.name());
        tc.setPercentage(req.percentage());
        tc.setCreditAccountType(req.creditAccountType());
        tc.setCreditAccountId(req.creditAccountId());
        tc.setDebitAccountType(req.debitAccountType());
        tc.setDebitAccountId(req.debitAccountId());
        tc.setStartDate(req.startDate());
        TaxComponent saved = componentRepository.save(tc);
        auditLogService.log("TaxComponent", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteComponent(UUID id) {
        TaxComponent tc = getComponent(id);
        componentRepository.delete(tc);
        auditLogService.log("TaxComponent", id.toString(), "DELETE", null, null);
    }

    // ── Tax Groups ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TaxGroup> listGroups(Pageable pageable) {
        return groupRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public TaxGroup getGroup(UUID id) {
        return groupRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("TaxGroup", id));
    }

    @Transactional
    public TaxGroup createGroup(CreateTaxGroupRequest req) {
        if (groupRepository.existsByName(req.name())) {
            throw CbaException.conflict("TAX_GROUP_NAME_EXISTS",
                "Tax group '" + req.name() + "' already exists");
        }
        TaxGroup group = new TaxGroup();
        group.setName(req.name());
        group.setStartDate(req.startDate());
        if (req.taxComponentIds() != null) {
            for (UUID componentId : req.taxComponentIds()) {
                group.getTaxComponents().add(getComponent(componentId));
            }
        }
        TaxGroup saved = groupRepository.save(group);
        auditLogService.log("TaxGroup", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public TaxGroup updateGroup(UUID id, CreateTaxGroupRequest req) {
        TaxGroup group = getGroup(id);
        if (!group.getName().equals(req.name()) && groupRepository.existsByName(req.name())) {
            throw CbaException.conflict("TAX_GROUP_NAME_EXISTS",
                "Tax group '" + req.name() + "' already exists");
        }
        group.setName(req.name());
        group.setStartDate(req.startDate());
        group.getTaxComponents().clear();
        if (req.taxComponentIds() != null) {
            for (UUID componentId : req.taxComponentIds()) {
                group.getTaxComponents().add(getComponent(componentId));
            }
        }
        TaxGroup saved = groupRepository.save(group);
        auditLogService.log("TaxGroup", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteGroup(UUID id) {
        TaxGroup group = getGroup(id);
        groupRepository.delete(group);
        auditLogService.log("TaxGroup", id.toString(), "DELETE", null, null);
    }
}

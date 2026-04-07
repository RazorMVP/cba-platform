package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MakerCheckerService {

    public record CreateMakerCheckerRequest(
        String actionName,
        String entityName,
        UUID entityId,
        String commandAsJson,
        UUID madeByUserId
    ) {}

    private final MakerCheckerRepository makerCheckerRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<MakerChecker> listPending(Pageable p) {
        return makerCheckerRepository.findByStatus(MakerChecker.Status.PENDING, p);
    }

    @Transactional(readOnly = true)
    public MakerChecker get(UUID id) {
        return makerCheckerRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("MakerChecker", id));
    }

    @Transactional
    public MakerChecker create(CreateMakerCheckerRequest req) {
        MakerChecker mc = new MakerChecker();
        mc.setActionName(req.actionName());
        mc.setEntityName(req.entityName());
        mc.setEntityId(req.entityId());
        mc.setCommandAsJson(req.commandAsJson());
        mc.setMadeByUserId(req.madeByUserId());
        MakerChecker saved = makerCheckerRepository.save(mc);
        auditLogService.log("MakerChecker", saved.getId().toString(), "MAKE", null, saved);
        return saved;
    }

    @Transactional
    public MakerChecker approve(UUID id, UUID checkerUserId) {
        MakerChecker mc = get(id);
        if (mc.getStatus() != MakerChecker.Status.PENDING)
            throw CbaException.badRequest("NOT_PENDING", "Entry is not in PENDING state");
        mc.setStatus(MakerChecker.Status.APPROVED);
        mc.setCheckedByUserId(checkerUserId);
        mc.setCheckedOnDate(OffsetDateTime.now());
        mc.setProcessingResult("APPROVED");
        MakerChecker saved = makerCheckerRepository.save(mc);
        auditLogService.log("MakerChecker", id.toString(), "APPROVE", null, saved);
        return saved;
    }

    @Transactional
    public MakerChecker reject(UUID id, UUID checkerUserId) {
        MakerChecker mc = get(id);
        if (mc.getStatus() != MakerChecker.Status.PENDING)
            throw CbaException.badRequest("NOT_PENDING", "Entry is not in PENDING state");
        mc.setStatus(MakerChecker.Status.REJECTED);
        mc.setCheckedByUserId(checkerUserId);
        mc.setCheckedOnDate(OffsetDateTime.now());
        mc.setProcessingResult("REJECTED");
        MakerChecker saved = makerCheckerRepository.save(mc);
        auditLogService.log("MakerChecker", id.toString(), "REJECT", null, saved);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        MakerChecker mc = get(id);
        if (mc.getStatus() != MakerChecker.Status.PENDING)
            throw CbaException.badRequest("NOT_PENDING", "Only PENDING entries can be deleted");
        makerCheckerRepository.delete(mc);
        auditLogService.log("MakerChecker", id.toString(), "DELETE", null, null);
    }
}

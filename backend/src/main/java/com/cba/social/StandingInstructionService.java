package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StandingInstructionService {

    public record CreateInstructionRequest(
        String name,
        UUID clientId,
        UUID fromAccountId,
        String fromAccountType,
        UUID toClientId,
        UUID toAccountId,
        String toAccountType,
        StandingInstruction.InstructionType instructionType,
        StandingInstruction.Priority priority,
        BigDecimal amount,
        LocalDate validityFromDate,
        LocalDate validityTillDate,
        StandingInstruction.RecurrenceType recurrenceType,
        int recurrenceFrequency,
        int recurrenceInterval,
        LocalDate nextRunForDate
    ) {}

    private final StandingInstructionRepository repository;
    private final AuditLogService               auditLogService;

    @Transactional(readOnly = true)
    public Page<StandingInstruction> listInstructions(Pageable p) {
        return repository.findAll(p);
    }

    @Transactional(readOnly = true)
    public StandingInstruction getInstruction(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> CbaException.notFound("StandingInstruction", id));
    }

    @Transactional
    public StandingInstruction createInstruction(CreateInstructionRequest req) {
        StandingInstruction si = new StandingInstruction();
        applyRequest(si, req);
        StandingInstruction saved = repository.save(si);
        auditLogService.log("StandingInstruction", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public StandingInstruction updateInstruction(UUID id, CreateInstructionRequest req) {
        StandingInstruction si = getInstruction(id);
        applyRequest(si, req);
        StandingInstruction saved = repository.save(si);
        auditLogService.log("StandingInstruction", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public StandingInstruction disable(UUID id) {
        StandingInstruction si = getInstruction(id);
        si.setStatus(StandingInstruction.Status.DISABLED);
        StandingInstruction saved = repository.save(si);
        auditLogService.log("StandingInstruction", id.toString(), "DISABLE", null, saved);
        return saved;
    }

    @Transactional
    public StandingInstruction enable(UUID id) {
        StandingInstruction si = getInstruction(id);
        si.setStatus(StandingInstruction.Status.ACTIVE);
        StandingInstruction saved = repository.save(si);
        auditLogService.log("StandingInstruction", id.toString(), "ENABLE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteInstruction(UUID id) {
        StandingInstruction si = getInstruction(id);
        si.setStatus(StandingInstruction.Status.DELETED);
        repository.save(si);
        auditLogService.log("StandingInstruction", id.toString(), "DELETE", null, null);
    }

    private void applyRequest(StandingInstruction si, CreateInstructionRequest req) {
        si.setName(req.name());
        si.setClientId(req.clientId());
        si.setFromAccountId(req.fromAccountId());
        si.setFromAccountType(req.fromAccountType() != null ? req.fromAccountType() : "SAVINGS");
        si.setToClientId(req.toClientId());
        si.setToAccountId(req.toAccountId());
        si.setToAccountType(req.toAccountType() != null ? req.toAccountType() : "SAVINGS");
        si.setInstructionType(req.instructionType() != null ? req.instructionType() : StandingInstruction.InstructionType.FIXED);
        si.setPriority(req.priority() != null ? req.priority() : StandingInstruction.Priority.MEDIUM);
        si.setAmount(req.amount());
        si.setValidityFromDate(req.validityFromDate());
        si.setValidityTillDate(req.validityTillDate());
        si.setRecurrenceType(req.recurrenceType() != null ? req.recurrenceType() : StandingInstruction.RecurrenceType.PERIODIC_RECURRENCE);
        si.setRecurrenceFrequency(req.recurrenceFrequency() > 0 ? req.recurrenceFrequency() : 1);
        si.setRecurrenceInterval(req.recurrenceInterval() > 0 ? req.recurrenceInterval() : 1);
        si.setNextRunForDate(req.nextRunForDate());
    }
}

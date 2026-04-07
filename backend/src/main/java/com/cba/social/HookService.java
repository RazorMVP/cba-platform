package com.cba.social;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HookService {

    public record CreateHookRequest(
        String name, Hook.HookType hookType,
        String payloadUrl, String contentType, String secretKey,
        List<String> events, boolean active
    ) {}

    public record CreateHolidayRequest(
        String name, LocalDate fromDate, LocalDate toDate,
        Holiday.RepaymentSchedulingType repaymentSchedulingType,
        LocalDate rescheduledRepaymentDate
    ) {}

    private final HookRepository hookRepository;
    private final HolidayRepository holidayRepository;
    private final AuditLogService auditLogService;

    // ── Hooks ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Hook> listHooks(Pageable p) { return hookRepository.findAll(p); }

    @Transactional(readOnly = true)
    public Hook getHook(UUID id) {
        return hookRepository.findById(id).orElseThrow(() -> CbaException.notFound("Hook", id));
    }

    @Transactional
    public Hook createHook(CreateHookRequest req) {
        Hook hook = new Hook();
        hook.setName(req.name());
        hook.setHookType(req.hookType() != null ? req.hookType() : Hook.HookType.WEB);
        hook.setPayloadUrl(req.payloadUrl());
        hook.setContentType(req.contentType() != null ? req.contentType() : "application/json");
        hook.setSecretKey(req.secretKey());
        hook.setEvents(req.events());
        hook.setActive(req.active());
        Hook saved = hookRepository.save(hook);
        auditLogService.log("Hook", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public Hook updateHook(UUID id, CreateHookRequest req) {
        Hook hook = getHook(id);
        hook.setName(req.name());
        hook.setHookType(req.hookType());
        hook.setPayloadUrl(req.payloadUrl());
        hook.setContentType(req.contentType());
        hook.setSecretKey(req.secretKey());
        hook.setEvents(req.events());
        hook.setActive(req.active());
        Hook saved = hookRepository.save(hook);
        auditLogService.log("Hook", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteHook(UUID id) {
        hookRepository.delete(getHook(id));
        auditLogService.log("Hook", id.toString(), "DELETE", null, null);
    }

    // ── Holidays ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Holiday> listHolidays(Pageable p) { return holidayRepository.findAll(p); }

    @Transactional(readOnly = true)
    public Holiday getHoliday(UUID id) {
        return holidayRepository.findById(id).orElseThrow(() -> CbaException.notFound("Holiday", id));
    }

    @Transactional
    public Holiday createHoliday(CreateHolidayRequest req) {
        Holiday h = new Holiday();
        h.setName(req.name());
        h.setFromDate(req.fromDate());
        h.setToDate(req.toDate());
        if (req.repaymentSchedulingType() != null)
            h.setRepaymentSchedulingType(req.repaymentSchedulingType());
        h.setRescheduledRepaymentDate(req.rescheduledRepaymentDate());
        Holiday saved = holidayRepository.save(h);
        auditLogService.log("Holiday", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public Holiday activateHoliday(UUID id) {
        Holiday h = getHoliday(id);
        h.setStatus(Holiday.Status.ACTIVE);
        Holiday saved = holidayRepository.save(h);
        auditLogService.log("Holiday", id.toString(), "ACTIVATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteHoliday(UUID id) {
        holidayRepository.delete(getHoliday(id));
        auditLogService.log("Holiday", id.toString(), "DELETE", null, null);
    }
}

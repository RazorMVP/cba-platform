package com.cba.accounting;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountingRuleService {

    public record CreateRuleRequest(
        String name,
        String description,
        UUID debitAccountId,
        UUID creditAccountId,
        boolean allowMultipleDebits,
        boolean allowMultipleCredits,
        boolean active
    ) {}

    private final AccountingRuleRepository ruleRepository;
    private final AuditLogService          auditLogService;

    @Transactional(readOnly = true)
    public Page<AccountingRule> listRules(Pageable p) {
        return ruleRepository.findAll(p);
    }

    @Transactional(readOnly = true)
    public AccountingRule getRule(UUID id) {
        return ruleRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("AccountingRule", id));
    }

    @Transactional
    public AccountingRule createRule(CreateRuleRequest req) {
        AccountingRule r = new AccountingRule();
        applyRequest(r, req);
        AccountingRule saved = ruleRepository.save(r);
        auditLogService.log("AccountingRule", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public AccountingRule updateRule(UUID id, CreateRuleRequest req) {
        AccountingRule r = getRule(id);
        applyRequest(r, req);
        AccountingRule saved = ruleRepository.save(r);
        auditLogService.log("AccountingRule", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteRule(UUID id) {
        ruleRepository.delete(getRule(id));
        auditLogService.log("AccountingRule", id.toString(), "DELETE", null, null);
    }

    private void applyRequest(AccountingRule r, CreateRuleRequest req) {
        r.setName(req.name());
        r.setDescription(req.description());
        r.setDebitAccountId(req.debitAccountId());
        r.setCreditAccountId(req.creditAccountId());
        r.setAllowMultipleDebits(req.allowMultipleDebits());
        r.setAllowMultipleCredits(req.allowMultipleCredits());
        r.setActive(req.active());
    }
}

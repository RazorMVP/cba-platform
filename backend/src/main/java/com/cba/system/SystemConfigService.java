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
public class SystemConfigService {

    public record CreateCodeRequest(String name) {}
    public record CreateCodeValueRequest(String label, Integer position, String description) {}
    public record UpdateGlobalConfigRequest(String stringValue, Long numericValue, Boolean booleanValue, boolean enabled) {}
    public record CreateFundRequest(String name, String externalId) {}
    public record CreatePaymentTypeRequest(String name, String description, boolean cashPayment, Integer position) {}
    public record UpdateAccountNumberFormatRequest(AccountNumberFormat.PrefixType prefixType, String prefixCharacter) {}

    private final CodeRepository codeRepository;
    private final CodeValueRepository codeValueRepository;
    private final GlobalConfigurationRepository globalConfigRepository;
    private final FundRepository fundRepository;
    private final SystemPaymentTypeRepository paymentTypeRepository;
    private final AccountNumberFormatRepository accountNumberFormatRepository;
    private final AuditLogService auditLogService;

    // ── Codes ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Code> listCodes(Pageable p) { return codeRepository.findAll(p); }

    @Transactional(readOnly = true)
    public Code getCode(UUID id) {
        return codeRepository.findById(id).orElseThrow(() -> CbaException.notFound("Code", id));
    }

    @Transactional
    public Code createCode(CreateCodeRequest req) {
        if (codeRepository.existsByName(req.name()))
            throw CbaException.conflict("CODE_EXISTS", "Code '" + req.name() + "' already exists");
        Code code = new Code();
        code.setName(req.name());
        Code saved = codeRepository.save(code);
        auditLogService.log("Code", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteCode(UUID id) {
        Code code = getCode(id);
        if (code.isSystemDefined())
            throw CbaException.badRequest("SYSTEM_DEFINED", "Cannot delete system-defined codes");
        codeRepository.delete(code);
        auditLogService.log("Code", id.toString(), "DELETE", null, null);
    }

    @Transactional(readOnly = true)
    public Page<CodeValue> listCodeValues(UUID codeId, Pageable p) {
        getCode(codeId);
        return codeValueRepository.findByCodeId(codeId, p);
    }

    @Transactional
    public CodeValue createCodeValue(UUID codeId, CreateCodeValueRequest req) {
        Code code = getCode(codeId);
        CodeValue cv = new CodeValue();
        cv.setCode(code);
        cv.setLabel(req.label());
        cv.setPosition(req.position());
        cv.setDescription(req.description());
        CodeValue saved = codeValueRepository.save(cv);
        auditLogService.log("CodeValue", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteCodeValue(UUID codeId, UUID valueId) {
        CodeValue cv = codeValueRepository.findById(valueId)
            .orElseThrow(() -> CbaException.notFound("CodeValue", valueId));
        codeValueRepository.delete(cv);
        auditLogService.log("CodeValue", valueId.toString(), "DELETE", null, null);
    }

    // ── Global Configurations ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GlobalConfiguration> listConfigs() { return globalConfigRepository.findAll(); }

    @Transactional(readOnly = true)
    public GlobalConfiguration getConfig(UUID id) {
        return globalConfigRepository.findById(id).orElseThrow(() -> CbaException.notFound("GlobalConfiguration", id));
    }

    @Transactional
    public GlobalConfiguration updateConfig(UUID id, UpdateGlobalConfigRequest req) {
        GlobalConfiguration config = getConfig(id);
        config.setStringValue(req.stringValue());
        config.setNumericValue(req.numericValue());
        config.setBooleanValue(req.booleanValue());
        config.setEnabled(req.enabled());
        GlobalConfiguration saved = globalConfigRepository.save(config);
        auditLogService.log("GlobalConfiguration", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    // ── Funds ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Fund> listFunds(Pageable p) { return fundRepository.findAll(p); }

    @Transactional(readOnly = true)
    public Fund getFund(UUID id) {
        return fundRepository.findById(id).orElseThrow(() -> CbaException.notFound("Fund", id));
    }

    @Transactional
    public Fund createFund(CreateFundRequest req) {
        if (fundRepository.existsByName(req.name()))
            throw CbaException.conflict("FUND_EXISTS", "Fund '" + req.name() + "' already exists");
        Fund fund = new Fund();
        fund.setName(req.name());
        fund.setExternalId(req.externalId());
        Fund saved = fundRepository.save(fund);
        auditLogService.log("Fund", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public Fund updateFund(UUID id, CreateFundRequest req) {
        Fund fund = getFund(id);
        fund.setName(req.name());
        fund.setExternalId(req.externalId());
        Fund saved = fundRepository.save(fund);
        auditLogService.log("Fund", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteFund(UUID id) {
        Fund fund = getFund(id);
        fundRepository.delete(fund);
        auditLogService.log("Fund", id.toString(), "DELETE", null, null);
    }

    // ── Payment Types ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SystemPaymentType> listPaymentTypes(Pageable p) { return paymentTypeRepository.findAll(p); }

    @Transactional(readOnly = true)
    public SystemPaymentType getPaymentType(UUID id) {
        return paymentTypeRepository.findById(id).orElseThrow(() -> CbaException.notFound("PaymentType", id));
    }

    @Transactional
    public SystemPaymentType createPaymentType(CreatePaymentTypeRequest req) {
        if (paymentTypeRepository.existsByName(req.name()))
            throw CbaException.conflict("PAYMENT_TYPE_EXISTS", "Payment type '" + req.name() + "' already exists");
        SystemPaymentType pt = new SystemPaymentType();
        pt.setName(req.name());
        pt.setDescription(req.description());
        pt.setCashPayment(req.cashPayment());
        pt.setPosition(req.position());
        SystemPaymentType saved = paymentTypeRepository.save(pt);
        auditLogService.log("PaymentType", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public SystemPaymentType updatePaymentType(UUID id, CreatePaymentTypeRequest req) {
        SystemPaymentType pt = getPaymentType(id);
        pt.setName(req.name());
        pt.setDescription(req.description());
        pt.setCashPayment(req.cashPayment());
        pt.setPosition(req.position());
        SystemPaymentType saved = paymentTypeRepository.save(pt);
        auditLogService.log("PaymentType", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deletePaymentType(UUID id) {
        SystemPaymentType pt = getPaymentType(id);
        if (pt.isSystemDefined())
            throw CbaException.badRequest("SYSTEM_DEFINED", "Cannot delete system-defined payment types");
        paymentTypeRepository.delete(pt);
        auditLogService.log("PaymentType", id.toString(), "DELETE", null, null);
    }

    // ── Account Number Formats ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AccountNumberFormat> listAccountNumberFormats() { return accountNumberFormatRepository.findAll(); }

    @Transactional(readOnly = true)
    public AccountNumberFormat getAccountNumberFormat(UUID id) {
        return accountNumberFormatRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("AccountNumberFormat", id));
    }

    @Transactional
    public AccountNumberFormat updateAccountNumberFormat(UUID id, UpdateAccountNumberFormatRequest req) {
        AccountNumberFormat fmt = getAccountNumberFormat(id);
        fmt.setPrefixType(req.prefixType());
        fmt.setPrefixCharacter(req.prefixCharacter());
        AccountNumberFormat saved = accountNumberFormatRepository.save(fmt);
        auditLogService.log("AccountNumberFormat", id.toString(), "UPDATE", null, saved);
        return saved;
    }
}

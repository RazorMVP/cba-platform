package com.cba.account.algorithm;

import com.cba.account.AccountType;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.common.tenant.TenantContext;
import com.cba.tenant.Tenant;
import com.cba.tenant.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central orchestrator for account number generation and validation.
 *
 * <p><strong>Generation flow:</strong>
 * <ol>
 *   <li>Resolve current tenant from {@link TenantContext}</li>
 *   <li>Read {@link TenantAlgorithmConfig} from tenant's {@code country_params}</li>
 *   <li>Look up which algorithm is configured for the account type</li>
 *   <li>Delegate to that algorithm's {@link AccountNumberAlgorithm#generate}</li>
 *   <li>Fall back to {@link AlgorithmType#MIFOS} if none configured</li>
 * </ol>
 *
 * <p><strong>Validation flow:</strong>
 * Same tenant/algorithm resolution, then:
 * <ul>
 *   <li>If tenant's algorithm for the type is MIFOS → skip (no check digit)</li>
 *   <li>If NUBAN (or other) → validate; throw on failure if mode is STRICT/PARANOID</li>
 *   <li>If validationMode is WARN (future) → log and continue</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountNumberAlgorithmService {

    private final List<AccountNumberAlgorithm>  algorithms;
    private final TenantService                 tenantService;
    private final AuditLogService               auditLogService;
    private final ObjectMapper                  objectMapper;

    private Map<AlgorithmType, AccountNumberAlgorithm> algorithmMap() {
        return algorithms.stream()
                .collect(Collectors.toMap(AccountNumberAlgorithm::getType, Function.identity()));
    }

    // ── Generation ────────────────────────────────────────────────────

    /**
     * Generates an account number for the current tenant's account type.
     * Uses the algorithm configured in {@code country_params}; falls back to MIFOS.
     *
     * @param accountType account type being opened
     * @param branchCode  branch code — used by the MIFOS fallback
     * @return generated account number string
     */
    public String generate(AccountType accountType, String branchCode) {
        AlgorithmContext ctx = buildContext(accountType, branchCode);
        AlgorithmType    type = ctx.config().algorithmFor(accountType);
        AccountNumberAlgorithm algo = algorithmMap().getOrDefault(type,
                algorithmMap().get(AlgorithmType.MIFOS));

        String number = algo.generate(ctx);
        log.debug("Generated {} account number {} for tenant={} type={}",
                type, number, ctx.tenantId(), accountType);
        return number;
    }

    // ── Validation ────────────────────────────────────────────────────

    /**
     * Validates an account number for the given account type in the current tenant context.
     * <p>Called at three integration points:
     * <ul>
     *   <li>Account creation (own-bank PARANOID mode)</li>
     *   <li>Payment destination</li>
     *   <li>Beneficiary registration</li>
     * </ul>
     *
     * @param accountNumber the number to validate
     * @param accountType   the expected account type
     * @throws CbaException (400) when validation fails and mode is STRICT or PARANOID
     */
    public void validateOrThrow(String accountNumber, AccountType accountType) {
        if (accountNumber == null || accountNumber.isBlank()) return;

        AlgorithmContext       ctx    = buildContext(accountType, null);
        AlgorithmType          type   = ctx.config().algorithmFor(accountType);
        AccountNumberAlgorithm algo   = algorithmMap().getOrDefault(type,
                algorithmMap().get(AlgorithmType.MIFOS));
        ValidationResult       result = algo.validate(accountNumber, ctx);

        if (!result.valid()) {
            log.warn("Account number validation failed: code={} number={}",
                    result.errorCode(), accountNumber);
            throw CbaException.badRequest(result.errorCode(), result.message());
        }
    }

    /**
     * Validates an external payment destination account number.
     * Uses STRICT mode (check digit only, any bank) unless the tenant
     * is configured for PARANOID.
     */
    public void validatePaymentDestination(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return;

        // Use SAVINGS as the representative account type for external validation
        // (NUBAN applies uniformly to all deposit account types)
        AlgorithmContext       ctx    = buildContext(AccountType.SAVINGS, null);
        AlgorithmType          type   = ctx.config().algorithmFor(AccountType.SAVINGS);
        AccountNumberAlgorithm algo   = algorithmMap().getOrDefault(type,
                algorithmMap().get(AlgorithmType.MIFOS));
        ValidationResult       result = algo.validate(accountNumber, ctx);

        if (!result.valid()) {
            log.warn("Payment destination validation failed: code={} number={}",
                    result.errorCode(), accountNumber);
            throw CbaException.badRequest(result.errorCode(), result.message());
        }
    }

    // ── Configuration management ──────────────────────────────────────

    /**
     * Returns the current algorithm configuration for a tenant.
     */
    @Transactional(readOnly = true)
    public TenantAlgorithmConfig getConfig(UUID tenantId) {
        Tenant tenant = tenantService.getTenantById(tenantId);
        return parseConfig(tenant);
    }

    /**
     * Updates the algorithm configuration for a tenant.
     * Validates the request before persisting to prevent misconfiguration.
     */
    @Transactional
    @CacheEvict(value = "tenants", allEntries = true)
    public TenantAlgorithmConfig updateConfig(UUID tenantId, TenantAlgorithmConfig config) {
        Tenant tenant = tenantService.getTenantById(tenantId);

        // Validate: if any account type uses NUBAN, a bank code is required
        boolean nubanRequested = config.algorithms() != null
                && config.algorithms().values().stream()
                         .anyMatch(v -> AlgorithmType.NUBAN.name().equalsIgnoreCase(v));
        if (nubanRequested && !config.hasBankCode()) {
            throw CbaException.badRequest("NUBAN_BANK_CODE_REQUIRED",
                    "bankCode is required when any account type uses the NUBAN algorithm");
        }
        if (config.hasBankCode() && !config.bankCode().matches("\\d{3}")) {
            throw CbaException.badRequest("NUBAN_BANK_CODE_INVALID",
                    "bankCode must be exactly 3 digits");
        }

        try {
            String json = objectMapper.writeValueAsString(config);
            tenant.setCountryParams(json);
        } catch (Exception e) {
            throw CbaException.badRequest("CONFIG_SERIALIZATION_ERROR",
                    "Failed to serialize algorithm config: " + e.getMessage());
        }

        auditLogService.log("Tenant", tenantId.toString(), "UPDATE_ALGORITHM_CONFIG", null, config);
        return config;
    }

    // ── Private helpers ───────────────────────────────────────────────

    private AlgorithmContext buildContext(AccountType accountType, String branchCode) {
        String tenantCode = TenantContext.getTenant();
        Tenant tenant;

        if (tenantCode != null) {
            tenant = tenantService.getTenantByCode(tenantCode);
        } else {
            tenant = tenantService.getDefaultTenant();
        }

        TenantAlgorithmConfig config = parseConfig(tenant);
        return new AlgorithmContext(tenant.getId(), accountType, config,
                branchCode != null ? branchCode : "001");
    }

    private TenantAlgorithmConfig parseConfig(Tenant tenant) {
        String json = tenant.getCountryParams();
        if (json == null || json.isBlank() || json.equals("{}")) {
            return TenantAlgorithmConfig.empty();
        }
        try {
            return objectMapper.readValue(json, TenantAlgorithmConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse country_params for tenant {}: {}",
                    tenant.getCode(), e.getMessage());
            return TenantAlgorithmConfig.empty();
        }
    }
}

package com.cba.account.algorithm;

import com.cba.account.AccountType;

import java.util.UUID;

/**
 * Carries all per-request context an algorithm needs to generate or validate
 * an account number. Constructed by {@link AccountNumberAlgorithmService}
 * from the current tenant state.
 *
 * @param tenantId    Current tenant UUID
 * @param accountType Account type being generated or validated
 * @param config      Algorithm configuration for this tenant
 * @param branchCode  Branch code — used by the MIFOS fallback algorithm
 */
public record AlgorithmContext(
        UUID tenantId,
        AccountType accountType,
        TenantAlgorithmConfig config,
        String branchCode
) {

    /** Convenience accessor — avoids repeated null checks in algorithm code. */
    public String bankCode() {
        return config != null ? config.bankCode() : null;
    }

    public ValidationMode validationMode() {
        return config != null ? config.effectiveValidationMode() : ValidationMode.STRICT;
    }
}

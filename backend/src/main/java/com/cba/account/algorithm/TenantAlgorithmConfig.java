package com.cba.account.algorithm;

import com.cba.account.AccountType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Typed view of a tenant's {@code country_params} JSONB column.
 *
 * Example JSON stored in tenants.country_params:
 * <pre>{@code
 * {
 *   "bankCode": "058",
 *   "validationMode": "STRICT",
 *   "algorithms": {
 *     "SAVINGS":       "NUBAN",
 *     "CHECKING":      "NUBAN",
 *     "FIXED_DEPOSIT": "MIFOS",
 *     "LOAN":          "MIFOS",
 *     "SHARE":         "MIFOS"
 *   }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantAlgorithmConfig(
        String bankCode,
        ValidationMode validationMode,
        Map<String, String> algorithms
) {

    /** Returns a config where every account type falls back to MIFOS. */
    public static TenantAlgorithmConfig empty() {
        return new TenantAlgorithmConfig(null, ValidationMode.STRICT, Map.of());
    }

    /**
     * Resolves which algorithm to use for the given account type.
     * Falls back to {@link AlgorithmType#MIFOS} if not configured.
     */
    public AlgorithmType algorithmFor(AccountType accountType) {
        if (algorithms == null || algorithms.isEmpty()) return AlgorithmType.MIFOS;
        String raw = algorithms.getOrDefault(accountType.name(), "MIFOS");
        try {
            return AlgorithmType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AlgorithmType.MIFOS;
        }
    }

    /**
     * Resolves which algorithm to use for a named account type string.
     * Used when the exact {@link AccountType} is not available (e.g. LOAN, SHARE).
     */
    public AlgorithmType algorithmForName(String accountTypeName) {
        if (algorithms == null || algorithms.isEmpty()) return AlgorithmType.MIFOS;
        String raw = algorithms.getOrDefault(accountTypeName.toUpperCase(), "MIFOS");
        try {
            return AlgorithmType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AlgorithmType.MIFOS;
        }
    }

    public ValidationMode effectiveValidationMode() {
        return validationMode != null ? validationMode : ValidationMode.STRICT;
    }

    public boolean hasBankCode() {
        return bankCode != null && !bankCode.isBlank();
    }
}

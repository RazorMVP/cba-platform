package com.cba.system;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityPolicyService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm:cba}")
    private String realm;

    public record SecurityPolicy(
            // Brute-force
            boolean bruteForceProtected,
            int maxLoginFailures,
            int lockoutDurationSeconds,
            int failureResetWindowSeconds,

            // Password
            int minPasswordLength,
            boolean requireUppercase,
            boolean requireLowercase,
            boolean requireDigits,
            boolean requireSpecialChars,
            int passwordHistoryCount,

            // Sessions / tokens
            int ssoSessionIdleTimeoutSeconds,
            int ssoSessionMaxLifespanSeconds,
            int accessTokenLifespanSeconds,

            // Raw Keycloak password policy string (read-only, informational)
            String rawPasswordPolicy
    ) {}

    public record UpdateSecurityPolicyRequest(
            Boolean bruteForceProtected,
            Integer maxLoginFailures,
            Integer lockoutDurationSeconds,
            Integer failureResetWindowSeconds,
            Integer minPasswordLength,
            Boolean requireUppercase,
            Boolean requireLowercase,
            Boolean requireDigits,
            Boolean requireSpecialChars,
            Integer passwordHistoryCount,
            Integer ssoSessionIdleTimeoutSeconds,
            Integer ssoSessionMaxLifespanSeconds,
            Integer accessTokenLifespanSeconds
    ) {}

    public SecurityPolicy getPolicy() {
        RealmRepresentation r;
        try {
            r = keycloak.realm(realm).toRepresentation();
        } catch (Exception e) {
            log.warn("Keycloak unavailable — returning default security policy: {}", e.getMessage());
            return defaultPolicy();
        }
        String policy = r.getPasswordPolicy() != null ? r.getPasswordPolicy() : "";

        return new SecurityPolicy(
                Boolean.TRUE.equals(r.isBruteForceProtected()),
                r.getFailureFactor() != null ? r.getFailureFactor() : 5,
                r.getMaxFailureWaitSeconds() != null ? r.getMaxFailureWaitSeconds() : 900,
                r.getMaxDeltaTimeSeconds() != null ? r.getMaxDeltaTimeSeconds() : 43200,
                parsePasswordInt(policy, "length", 8),
                policy.contains("upperCase"),
                policy.contains("lowerCase"),
                policy.contains("digits"),
                policy.contains("specialChars"),
                parsePasswordInt(policy, "passwordHistory", 0),
                r.getSsoSessionIdleTimeout() != null ? r.getSsoSessionIdleTimeout() : 1800,
                r.getSsoSessionMaxLifespan() != null ? r.getSsoSessionMaxLifespan() : 36000,
                r.getAccessTokenLifespan() != null ? r.getAccessTokenLifespan() : 300,
                policy
        );
    }

    public SecurityPolicy updatePolicy(UpdateSecurityPolicyRequest req) {
        RealmRepresentation r = keycloak.realm(realm).toRepresentation();

        if (req.bruteForceProtected() != null)       r.setBruteForceProtected(req.bruteForceProtected());
        if (req.maxLoginFailures() != null)           r.setFailureFactor(req.maxLoginFailures());
        if (req.lockoutDurationSeconds() != null)     r.setMaxFailureWaitSeconds(req.lockoutDurationSeconds());
        if (req.failureResetWindowSeconds() != null)  r.setMaxDeltaTimeSeconds(req.failureResetWindowSeconds());
        if (req.ssoSessionIdleTimeoutSeconds() != null) r.setSsoSessionIdleTimeout(req.ssoSessionIdleTimeoutSeconds());
        if (req.ssoSessionMaxLifespanSeconds() != null) r.setSsoSessionMaxLifespan(req.ssoSessionMaxLifespanSeconds());
        if (req.accessTokenLifespanSeconds() != null) r.setAccessTokenLifespan(req.accessTokenLifespanSeconds());

        // Rebuild password policy string from individual flags
        r.setPasswordPolicy(buildPasswordPolicy(req, r.getPasswordPolicy()));

        keycloak.realm(realm).update(r);
        log.info("Security policy updated by admin");
        return getPolicy();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SecurityPolicy defaultPolicy() {
        return new SecurityPolicy(true, 5, 900, 43200, 8, true, true, true, false, 0, 1800, 36000, 300, "length(8) and upperCase and lowerCase and digits");
    }

    private String buildPasswordPolicy(UpdateSecurityPolicyRequest req, String existing) {
        // Parse existing clauses into a mutable map
        java.util.LinkedHashMap<String, String> clauses = new java.util.LinkedHashMap<>();
        if (existing != null && !existing.isBlank()) {
            for (String part : existing.split(" and ")) {
                String trimmed = part.trim();
                int paren = trimmed.indexOf('(');
                String key = paren >= 0 ? trimmed.substring(0, paren) : trimmed;
                clauses.put(key, trimmed);
            }
        }

        // Apply overrides
        applyClause(clauses, "length",          req.minPasswordLength(),      "length(%d)");
        applyBoolClause(clauses, "upperCase",   req.requireUppercase());
        applyBoolClause(clauses, "lowerCase",   req.requireLowercase());
        applyBoolClause(clauses, "digits",      req.requireDigits());
        applyBoolClause(clauses, "specialChars",req.requireSpecialChars());
        applyClause(clauses, "passwordHistory", req.passwordHistoryCount(),   "passwordHistory(%d)");

        return String.join(" and ", clauses.values());
    }

    private void applyClause(java.util.Map<String, String> map, String key, Integer value, String fmt) {
        if (value == null) return;
        if (value <= 0) map.remove(key);
        else map.put(key, fmt.formatted(value));
    }

    private void applyBoolClause(java.util.Map<String, String> map, String key, Boolean value) {
        if (value == null) return;
        if (Boolean.TRUE.equals(value)) map.put(key, key);
        else map.remove(key);
    }

    private int parsePasswordInt(String policy, String key, int defaultVal) {
        if (policy == null) return defaultVal;
        int idx = policy.indexOf(key + "(");
        if (idx < 0) return defaultVal;
        try {
            int start = idx + key.length() + 1;
            int end   = policy.indexOf(')', start);
            return Integer.parseInt(policy.substring(start, end).trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }
}

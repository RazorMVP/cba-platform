package com.cba.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes to the append-only audit_log table.
 * Uses REQUIRES_NEW so the audit entry persists even if the calling transaction rolls back.
 * This satisfies compliance: failed operations are still logged.
 *
 * oldValues / newValues are serialized to JSON strings via Jackson before storage so
 * that PostgreSQL's jsonb column always receives valid JSON regardless of the Java type
 * passed by callers (String, Map, record, enum name, etc.).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String entityType, String entityId, String action,
                    Object oldValues, Object newValues) {
        String actor = resolveActor();
        AuditLog entry = AuditLog.create(
                entityType, entityId, action, actor,
                toJson(oldValues),
                toJson(newValues));
        auditLogRepository.save(entry);
    }

    /** Serializes any value to a JSON string safe for PostgreSQL jsonb. Returns null for null input. */
    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("AuditLogService: failed to serialize audit value — falling back to string representation", e);
            // Wrap in a JSON string literal so jsonb still receives valid JSON
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        }
    }

    private String resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "system";
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            return username != null ? username : jwt.getSubject();
        }
        return auth.getName();
    }
}

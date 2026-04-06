package com.cba.audit;

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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String entityType, String entityId, String action,
                    Object oldValues, Object newValues) {
        String actor = resolveActor();
        AuditLog entry = AuditLog.create(entityType, entityId, action, actor, oldValues, newValues);
        auditLogRepository.save(entry);
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

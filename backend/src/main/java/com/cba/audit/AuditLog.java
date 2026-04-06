package com.cba.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log record. NEVER update or delete rows in this table.
 * Retention minimum: 7 years.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Object oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Object newValues;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    static AuditLog create(String entityType, String entityId, String action,
                           String changedBy, Object oldValues, Object newValues) {
        AuditLog log = new AuditLog();
        log.entityType = entityType;
        log.entityId = entityId;
        log.action = action;
        log.changedBy = changedBy;
        log.oldValues = oldValues;
        log.newValues = newValues;
        return log;
    }
}

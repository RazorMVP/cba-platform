package com.cba.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    Page<AuditLog> findByChangedBy(String changedBy, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndChangedAtBetween(
        String entityType, Instant from, Instant to, Pageable pageable);
}

package com.cba.audit;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Tag(name = "Audit Log", description = "Immutable platform audit trail — every state-changing operation recorded with before/after values, user and timestamp")
@RestController
@RequestMapping("/api/v1/audits")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @Operation(summary = "List all audit log entries (paginated)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<AuditLog>> list(Pageable pageable) {
        return ApiResponse.ok(auditLogRepository.findAll(pageable));
    }

    @Operation(summary = "Get a single audit log entry by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AuditLog> get(@PathVariable UUID id) {
        return ApiResponse.ok(auditLogRepository.findById(id)
            .orElseThrow(() -> com.cba.common.exception.CbaException.notFound("AuditLog", id)));
    }

    @Operation(summary = "Search audit logs by entityType, entityId, changedBy or date range")
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<AuditLog>> search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        if (entityType != null && entityId != null) {
            return ApiResponse.ok(auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable));
        }
        if (changedBy != null) {
            return ApiResponse.ok(auditLogRepository.findByChangedBy(changedBy, pageable));
        }
        if (entityType != null && from != null && to != null) {
            Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            return ApiResponse.ok(auditLogRepository.findByEntityTypeAndChangedAtBetween(
                entityType, fromInstant, toInstant, pageable));
        }
        return ApiResponse.ok(auditLogRepository.findAll(pageable));
    }
}

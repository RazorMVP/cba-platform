package com.cba.audit;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Tag(name = "Compliance Reports", description = "Pre-built compliance and regulatory reports — audit summary, access patterns, risk indicators")
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ComplianceReportController {

    private final JdbcTemplate jdbc;

    @Operation(summary = "Audit event summary grouped by action type for the last N days")
    @GetMapping("/reports/audit-summary")
    public ApiResponse<List<Map<String, Object>>> auditSummary(
            @RequestParam(defaultValue = "30") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT action, entity_type, COUNT(*) AS event_count,
                   COUNT(DISTINCT changed_by) AS unique_actors
            FROM audit_log
            WHERE changed_at >= ?
            GROUP BY action, entity_type
            ORDER BY event_count DESC
            LIMIT 50
            """, Timestamp.from(since));
        return ApiResponse.ok(rows);
    }

    @Operation(summary = "Failed login attempts summary for the last N days")
    @GetMapping("/reports/failed-logins")
    public ApiResponse<List<Map<String, Object>>> failedLogins(
            @RequestParam(defaultValue = "30") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT username, ip_address, status,
                   COUNT(*) AS attempt_count,
                   MAX(created_at) AS last_attempt
            FROM login_history
            WHERE status IN ('FAILURE','LOCKED') AND created_at >= ?
            GROUP BY username, ip_address, status
            ORDER BY attempt_count DESC
            LIMIT 50
            """, Timestamp.from(since));
        return ApiResponse.ok(rows);
    }

    @Operation(summary = "Per-user activity counts across audit log for the last N days")
    @GetMapping("/reports/user-activity")
    public ApiResponse<List<Map<String, Object>>> userActivity(
            @RequestParam(defaultValue = "30") int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT changed_by AS user_id,
                   COUNT(*) AS total_actions,
                   COUNT(DISTINCT entity_type) AS entity_types_touched,
                   MAX(changed_at) AS last_action
            FROM audit_log
            WHERE changed_at >= ?
            GROUP BY changed_by
            ORDER BY total_actions DESC
            LIMIT 50
            """, Timestamp.from(since));
        return ApiResponse.ok(rows);
    }

    @Operation(summary = "High-value transaction access patterns for the last N days (amounts > threshold)")
    @GetMapping("/reports/data-access")
    public ApiResponse<List<Map<String, Object>>> dataAccess(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "LOAN") String entityType) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        // Blocks dangerous params to prevent injection
        String safeEntity = entityType.replaceAll("[^A-Z_]", "");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT entity_id, action, changed_by, changed_at
            FROM audit_log
            WHERE entity_type = ? AND changed_at >= ?
            ORDER BY changed_at DESC
            LIMIT 200
            """, safeEntity, Timestamp.from(since));
        return ApiResponse.ok(rows);
    }
}

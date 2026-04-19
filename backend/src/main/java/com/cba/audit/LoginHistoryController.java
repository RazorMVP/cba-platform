package com.cba.audit;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@Tag(name = "Login History", description = "System access log — every login, logout and authentication failure")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LoginHistoryController {

    private final LoginHistoryService svc;

    record RecordEventRequest(
        @NotBlank String status,
        String failureReason,
        String sessionRef
    ) {}

    @Operation(summary = "Record a login/logout event (called by the frontend on auth state change)")
    @PostMapping("/events")
    public ApiResponse<LoginHistory> recordEvent(
            @RequestBody RecordEventRequest req,
            Authentication auth,
            HttpServletRequest httpReq) {

        String userId   = auth != null ? auth.getName() : "anonymous";
        String username = userId;
        String ip       = extractIp(httpReq);
        String ua       = httpReq.getHeader("User-Agent");

        LoginHistory.Status status;
        try {
            status = LoginHistory.Status.valueOf(req.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            status = LoginHistory.Status.SUCCESS;
        }

        return ApiResponse.ok(svc.record(userId, username, ip, ua,
                status, req.failureReason(), req.sessionRef()));
    }

    @Operation(summary = "Search login history (ADMIN only)")
    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<org.springframework.data.domain.Page<LoginHistory>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        LoginHistory.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try { statusEnum = LoginHistory.Status.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        Instant fromInst = from != null ? from.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant toInst   = to   != null ? to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        Page<LoginHistory> result = svc.search(
                statusEnum,
                (username != null && !username.isBlank()) ? username : null,
                fromInst, toInst,
                PageRequest.of(page, size));

        return ApiResponse.ok(result);
    }

    @Operation(summary = "Login activity summary for the last N days (default 30)")
    @GetMapping("/events/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> summary(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(svc.summary(days));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}

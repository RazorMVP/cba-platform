package com.cba.card.settlement;

import com.cba.card.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Settlement batch management endpoints.
 *
 * <p>In normal operation the FEP drives batch lifecycle via ISO 8583 (0320/0322/0324).
 * These REST endpoints expose the same operations for ops tooling and the Angular UI.
 */
@RestController
@RequestMapping("/api/v1/cards/settlement")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SettlementController {

    private final SettlementService settlementService;
    private final SchemeSettlementService schemeSettlementService;
    private final ReconciliationService reconciliationService;

    /** List settlement batches, optionally filtered by date. */
    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<List<SettlementBatch>>> listBatches(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(settlementService.listBatches(date)));
    }

    /** Get a single batch. */
    @GetMapping("/batches/{id}")
    public ResponseEntity<ApiResponse<SettlementBatch>> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(settlementService.findBatchById(id)));
    }

    /** Open (or retrieve existing open) today's batch. */
    @PostMapping("/batches/open")
    public ResponseEntity<ApiResponse<SettlementBatch>> openBatch() {
        return ResponseEntity.ok(ApiResponse.ok(settlementService.openOrGetTodaysBatch()));
    }

    /** Close a batch and settle all pending items. */
    @PostMapping("/batches/{id}/close")
    public ResponseEntity<ApiResponse<SettlementBatch>> closeBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(settlementService.closeBatch(id)));
    }

    /** List all items in a batch. */
    @GetMapping("/batches/{id}/items")
    public ResponseEntity<ApiResponse<List<SettlementItem>>> getItems(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(settlementService.getItems(id)));
    }

    /**
     * Records a card scheme's settlement report (backlog 15b). The reported net and the expected net of the
     * date's cleared items and refunds go to the backend, which posts the cash leg (DR 2300 / CR 1003, or the
     * reverse) — a difference within its per-currency tolerance to GL 1004 (backlog 15d) — and the date's
     * batches become SETTLED. Above the tolerance the backend refuses: recorded=false, reason
     * DIFFERENCE_OVER_TOLERANCE with expected, reported and the difference, nothing kept. Idempotent for the
     * same report.
     */
    @PostMapping("/scheme-reports")
    public ResponseEntity<ApiResponse<SchemeReportResult>> recordSchemeReport(
            @Valid @RequestBody SchemeReportRequest request, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(schemeSettlementService.record(request,
                principal == null ? "system" : principal.getName())));
    }

    /** Recorded scheme settlement reports, optionally for one settlement date. */
    @GetMapping("/scheme-reports")
    public ResponseEntity<ApiResponse<List<SchemeSettlementReport>>> listSchemeReports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(schemeSettlementService.list(date)));
    }

    /**
     * Settlement reconciliation (backlog 15d): RC 91 declines whose backend hold was never released, items
     * claimed for clearing on an earlier date whose capture never answered, and items cleared without the
     * acquirer's country (no cross-border fee). {@code from}/{@code to} (inclusive) bound the authorization
     * date (holds) or the batch settlement date (items). Last four of the card only; at most 500 rows each.
     */
    @GetMapping("/reconciliation")
    public ResponseEntity<ApiResponse<ReconciliationService.ReconciliationView>> reconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(reconciliationService.view(from, to)));
    }

    /**
     * Retries the backend release of an unresolved hold (an RC 91 decline's reference) with the original
     * card-side amount and currency. When the backend answers — released, refunded (a 0200 debit that
     * committed after all), never seen (tombstoned so a late commit is refused) or already inactive — the
     * authorization is stamped resolved with the admin's name. 404 not an unresolved hold; 409
     * HOLD_ALREADY_RESOLVED or the backend's refusal reason; 503 BACKEND_UNAVAILABLE (nothing stamped).
     */
    @PostMapping("/reconciliation/holds/{authorizationId}/release")
    public ResponseEntity<ApiResponse<ReconciliationService.ResolvedHold>> retryHoldRelease(
            @PathVariable UUID authorizationId, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(reconciliationService.retryHoldRelease(authorizationId, actor(principal))));
    }

    /** The admin's name for the stamp: the token's preferred_username when present, else the principal name. */
    private static String actor(Principal principal) {
        if (principal == null) return "system";
        if (principal instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) return username;
        }
        return principal.getName();
    }

    /** The expected net for a scheme, settlement date and currency — what the scheme's report must show. */
    @GetMapping("/scheme-reports/expected")
    public ResponseEntity<ApiResponse<SchemeReportResult>> expectedSchemeNet(
            @RequestParam String scheme,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String currency) {
        return ResponseEntity.ok(ApiResponse.ok(schemeSettlementService.expected(scheme, date, currency)));
    }
}

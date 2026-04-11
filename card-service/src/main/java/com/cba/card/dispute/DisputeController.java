package com.cba.card.dispute;

import com.cba.card.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Dispute management endpoints.
 *
 * <p>State machine: RAISED → UNDER_REVIEW → RESOLVED_ISSUER | RESOLVED_ACQUIRER | WITHDRAWN
 */
@RestController
@RequestMapping("/api/v1/cards/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /** List all disputes, optionally filtered by status. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<List<CardDispute>>> listDisputes(
            @RequestParam(required = false) DisputeStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.findAll(status)));
    }

    /** Get a single dispute by ID. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardDispute>> getDispute(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.findById(id)));
    }

    /** Raise a new dispute. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ResponseEntity<ApiResponse<CardDispute>> raiseDispute(
            @Valid @RequestBody RaiseDisputeRequest req) {
        CardDispute dispute = disputeService.raiseDispute(
                req.cardId(), req.transactionRef(), req.disputeReason(),
                req.raisedBy(), req.originalAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dispute));
    }

    /**
     * Update dispute state via command param.
     * Commands: review, resolve_issuer, resolve_acquirer, withdraw
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardDispute>> updateDispute(
            @PathVariable UUID id,
            @RequestParam String command,
            @RequestBody(required = false) ResolveDisputeRequest req) {
        UUID resolvedBy = req != null ? req.resolvedBy() : null;
        String notes    = req != null ? req.resolutionNotes() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                disputeService.updateDispute(id, command, resolvedBy, notes)));
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    public record RaiseDisputeRequest(
            @NotNull UUID cardId,
            @NotBlank String transactionRef,
            @NotNull DisputeReason disputeReason,
            @NotNull UUID raisedBy,
            @NotNull BigDecimal originalAmount) {}

    public record ResolveDisputeRequest(
            UUID resolvedBy,
            String resolutionNotes) {}
}

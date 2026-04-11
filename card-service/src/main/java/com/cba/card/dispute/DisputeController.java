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
 * Scheme-compliant chargeback workflow REST API.
 *
 * Full state machine:
 *   POST /disputes                        -> raise
 *   POST /disputes/{id}/retrieval         -> RAISED -> RETRIEVAL_REQUESTED
 *   POST /disputes/{id}/chargeback        -> RAISED/RETRIEVAL_REQUESTED -> CHARGEBACK_INITIATED
 *   POST /disputes/{id}/representment     -> CHARGEBACK_INITIATED -> REPRESENTMENT
 *   POST /disputes/{id}/pre-arbitration   -> REPRESENTMENT -> PRE_ARBITRATION
 *   POST /disputes/{id}/resolve           -> any active -> RESOLVED
 *   POST /disputes/{id}/withdraw          -> any non-terminal -> WITHDRAWN
 *
 * Reference data:
 *   GET /disputes/reason-codes[?scheme=VISA]
 */
@RestController
@RequestMapping("/api/v1/cards/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    // ── Listing ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<List<CardDispute>>> listDisputes(
            @RequestParam(required = false) DisputeStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.findAll(status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardDispute>> getDispute(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.findById(id)));
    }

    @GetMapping("/{id}/retrieval-requests")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<List<RetrievalRequest>>> listRetrievalRequests(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.listRetrievalRequests(id)));
    }

    @GetMapping("/{id}/representments")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<List<Representment>>> listRepresentments(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.listRepresentments(id)));
    }

    // ── Reason codes (reference data) ─────────────────────────────────────────

    @GetMapping("/reason-codes")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<List<ChargebackReasonCode>>> listReasonCodes(
            @RequestParam(required = false) String scheme) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.listReasonCodes(scheme)));
    }

    // ── Raise ─────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ResponseEntity<ApiResponse<CardDispute>> raiseDispute(
            @Valid @RequestBody RaiseDisputeRequest req) {
        CardDispute dispute = disputeService.raiseDispute(
                req.cardId(), req.transactionRef(), req.disputeReason(),
                req.raisedBy(), req.originalAmount(), req.currencyCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dispute));
    }

    // ── Lifecycle commands ────────────────────────────────────────────────────

    /** RAISED -> RETRIEVAL_REQUESTED */
    @PostMapping("/{id}/retrieval")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardDispute>> requestRetrieval(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.requestRetrieval(id)));
    }

    /** RAISED/RETRIEVAL_REQUESTED -> CHARGEBACK_INITIATED */
    @PostMapping("/{id}/chargeback")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardDispute>> initiateChargeback(
            @PathVariable UUID id,
            @Valid @RequestBody InitiateChargebackRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                disputeService.initiateChargeback(id, req.reasonCodeId())));
    }

    /** CHARGEBACK_INITIATED -> REPRESENTMENT */
    @PostMapping("/{id}/representment")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardDispute>> recordRepresentment(
            @PathVariable UUID id,
            @Valid @RequestBody RepresentmentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                disputeService.recordRepresentment(id, req.acquirerReason())));
    }

    /** REPRESENTMENT -> PRE_ARBITRATION */
    @PostMapping("/{id}/pre-arbitration")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CardDispute>> escalateToPreArbitration(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.escalateToPreArbitration(id)));
    }

    /** Any active -> RESOLVED */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardDispute>> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                disputeService.resolve(id, req.resolvedBy(), req.resolutionFavor(), req.notes())));
    }

    /** Any non-terminal -> WITHDRAWN */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ResponseEntity<ApiResponse<CardDispute>> withdraw(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.withdraw(id)));
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    public record RaiseDisputeRequest(
            @NotNull UUID cardId,
            @NotBlank String transactionRef,
            @NotNull DisputeReason disputeReason,
            @NotNull UUID raisedBy,
            @NotNull BigDecimal originalAmount,
            String currencyCode) {}

    public record InitiateChargebackRequest(
            @NotNull UUID reasonCodeId) {}

    public record RepresentmentRequest(
            @NotBlank String acquirerReason) {}

    public record ResolveRequest(
            UUID resolvedBy,
            @NotBlank String resolutionFavor,
            String notes) {}
}

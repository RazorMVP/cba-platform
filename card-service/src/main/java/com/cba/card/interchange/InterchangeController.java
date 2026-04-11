package com.cba.card.interchange;

import com.cba.card.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Interchange Management REST API.
 *
 * <p>All endpoints require ADMIN role — interchange rates and scheme fees are
 * bank-level configuration managed by operations staff, not customer-facing.
 *
 * <p>Base path: {@code /api/v1/interchange}
 */
@RestController
@RequestMapping("/api/v1/interchange")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class InterchangeController {

    private final InterchangeService interchangeService;

    // ── Interchange Rates ─────────────────────────────────────────────────────

    /**
     * GET /api/v1/interchange/rates
     * List all active interchange rate tiers.
     */
    @GetMapping("/rates")
    public ResponseEntity<ApiResponse<List<InterchangeRate>>> listRates() {
        return ResponseEntity.ok(ApiResponse.ok(interchangeService.listRates()));
    }

    /**
     * GET /api/v1/interchange/rates/{id}
     */
    @GetMapping("/rates/{id}")
    public ResponseEntity<ApiResponse<InterchangeRate>> getRate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(interchangeService.getRate(id)));
    }

    /**
     * POST /api/v1/interchange/rates
     * Create a new interchange rate tier.
     */
    @PostMapping("/rates")
    public ResponseEntity<ApiResponse<InterchangeRate>> createRate(
            @Valid @RequestBody InterchangeRateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(interchangeService.createRate(req)));
    }

    /**
     * PUT /api/v1/interchange/rates/{id}
     * Update an existing rate tier.
     */
    @PutMapping("/rates/{id}")
    public ResponseEntity<ApiResponse<InterchangeRate>> updateRate(
            @PathVariable UUID id,
            @Valid @RequestBody InterchangeRateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(interchangeService.updateRate(id, req)));
    }

    /**
     * DELETE /api/v1/interchange/rates/{id}
     * Soft-delete (sets active=false).
     */
    @DeleteMapping("/rates/{id}")
    public ResponseEntity<Void> deleteRate(@PathVariable UUID id) {
        interchangeService.deleteRate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Scheme Fees ───────────────────────────────────────────────────────────

    /**
     * GET /api/v1/interchange/fees
     * List all active scheme assessment fees.
     */
    @GetMapping("/fees")
    public ResponseEntity<ApiResponse<List<SchemeFee>>> listFees() {
        return ResponseEntity.ok(ApiResponse.ok(interchangeService.listFees()));
    }

    /**
     * GET /api/v1/interchange/fees/{id}
     */
    @GetMapping("/fees/{id}")
    public ResponseEntity<ApiResponse<SchemeFee>> getFee(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(interchangeService.getFee(id)));
    }

    /**
     * POST /api/v1/interchange/fees
     */
    @PostMapping("/fees")
    public ResponseEntity<ApiResponse<SchemeFee>> createFee(
            @Valid @RequestBody SchemeFeeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(interchangeService.createFee(req)));
    }

    /**
     * PUT /api/v1/interchange/fees/{id}
     */
    @PutMapping("/fees/{id}")
    public ResponseEntity<ApiResponse<SchemeFee>> updateFee(
            @PathVariable UUID id,
            @Valid @RequestBody SchemeFeeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(interchangeService.updateFee(id, req)));
    }

    /**
     * DELETE /api/v1/interchange/fees/{id}
     * Soft-delete.
     */
    @DeleteMapping("/fees/{id}")
    public ResponseEntity<Void> deleteFee(@PathVariable UUID id) {
        interchangeService.deleteFee(id);
        return ResponseEntity.noContent().build();
    }

    // ── Calculation ───────────────────────────────────────────────────────────

    /**
     * GET /api/v1/interchange/calculate?authId={uuid}
     *
     * <p>Calculate interchange for a specific authorization log entry. Writes
     * the result to {@code interchange_log} and returns the breakdown.
     * Useful for operations staff to verify what interchange was (or would be)
     * applied to a particular transaction.
     */
    @GetMapping("/calculate")
    public ResponseEntity<ApiResponse<InterchangeResult>> calculate(
            @RequestParam UUID authId) {
        return ResponseEntity.ok(ApiResponse.ok(interchangeService.calculate(authId)));
    }

    /**
     * GET /api/v1/interchange/log/{authId}
     *
     * <p>Retrieve the most recently persisted interchange log entry for an auth.
     * Returns 200 with null data if no calculation has been run yet.
     */
    @GetMapping("/log/{authId}")
    public ResponseEntity<ApiResponse<InterchangeLog>> getLog(@PathVariable UUID authId) {
        return ResponseEntity.ok(ApiResponse.ok(interchangeService.getLogForAuth(authId)));
    }
}

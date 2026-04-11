package com.cba.card.settlement;

import com.cba.card.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
}

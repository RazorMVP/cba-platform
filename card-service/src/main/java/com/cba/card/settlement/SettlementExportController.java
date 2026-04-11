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
 * REST interface for the settlement file export subsystem.
 *
 * <h3>Two usage patterns</h3>
 * <ol>
 *   <li><b>Nightly automatic</b> — the {@link SettlementFileExportService} {@code @Scheduled}
 *       job fires at 23:58 and exports all CLOSED batches for today.</li>
 *   <li><b>Manual trigger</b> — ops staff hit {@code POST /export/{batchId}} to re-export a
 *       specific batch (e.g. after fixing credentials or after a transmission failure).</li>
 * </ol>
 *
 * <h3>Idempotency</h3>
 * Re-triggering an already-transmitted batch is safe — the service skips schemes that
 * already have a TRANSMITTED record for the batch.
 */
@RestController
@RequestMapping("/api/v1/cards/settlement")
@RequiredArgsConstructor
public class SettlementExportController {

    private final SettlementFileExportService exportService;

    // ── Manual export trigger ─────────────────────────────────────────────────

    /**
     * Manually trigger export for a specific settlement batch.
     *
     * <p>Use this when:
     * <ul>
     *   <li>The nightly cron failed and you need to re-export for a past date</li>
     *   <li>A scheme's credentials were just configured and you want to test transmission</li>
     *   <li>A specific scheme's file needs to be re-sent (e.g. SFTP timeout recovery)</li>
     * </ul>
     *
     * @param batchId        UUID of the settlement batch to export
     * @param settlementDate ISO date to embed in filenames; defaults to today if omitted
     */
    @PostMapping("/export/{batchId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SettlementTransmission>>> triggerExport(
            @PathVariable UUID batchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate settlementDate) {

        LocalDate date = (settlementDate != null) ? settlementDate : LocalDate.now();
        List<SettlementTransmission> results = exportService.exportBatch(batchId, date);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    // ── Transmission log ──────────────────────────────────────────────────────

    /**
     * List settlement file transmission records.
     *
     * @param status optional filter: {@code PENDING}, {@code TRANSMITTED}, {@code ACKNOWLEDGED}, {@code FAILED}
     */
    @GetMapping("/transmissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<ApiResponse<List<SettlementTransmission>>> listTransmissions(
            @RequestParam(required = false) String status) {

        return ResponseEntity.ok(ApiResponse.ok(exportService.listTransmissions(status)));
    }

    /**
     * Get a single transmission record by its UUID.
     */
    @GetMapping("/transmissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<ApiResponse<SettlementTransmission>> getTransmission(
            @PathVariable UUID id) {

        SettlementTransmission tx = exportService.getTransmission(id);
        return ResponseEntity.ok(ApiResponse.ok(tx));
    }

    /**
     * List all transmission records for a specific settlement batch.
     * Useful for reviewing which schemes succeeded and which failed for a given day.
     */
    @GetMapping("/batches/{batchId}/transmissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<ApiResponse<List<SettlementTransmission>>> listTransmissionsForBatch(
            @PathVariable UUID batchId) {

        return ResponseEntity.ok(ApiResponse.ok(exportService.listTransmissionsForBatch(batchId)));
    }
}

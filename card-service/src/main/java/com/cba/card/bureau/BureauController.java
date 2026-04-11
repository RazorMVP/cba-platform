package com.cba.card.bureau;

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
 * Card personalization bureau REST API.
 *
 * <p>All endpoints require {@code ADMIN} role — bureau operations are privileged
 * back-office functions, not customer-facing.
 *
 * <h3>Bureau job lifecycle</h3>
 * <pre>
 *   POST /jobs             → create PENDING job (collects all ORDERED cards)
 *   POST /jobs/{id}/submit → generate CDP data, mark SENT
 *   POST /jobs/{id}/confirm → bureau callback: mark CONFIRMED, advance card statuses to PRODUCED
 *   POST /jobs/{id}/dispatch → mark cards DISPATCHED, set dispatch date
 *   POST /jobs/{id}/fail   → mark job FAILED (transmission error or bureau rejection)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bureau")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BureauController {

    private final BureauService bureauService;

    // ── Job Management ────────────────────────────────────────────────────────

    /**
     * Create a new bureau job by collecting all physical card orders in ORDERED status.
     * Returns 400 if no orders are pending.
     */
    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<BureauJob>> createJob() {
        BureauJob job = bureauService.createJob();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(job));
    }

    /** List all bureau jobs, newest first. */
    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<BureauJob>>> listJobs() {
        return ResponseEntity.ok(ApiResponse.ok(bureauService.listAll()));
    }

    /** Get a single bureau job with its items. */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<BureauJobDetail>> getJob(@PathVariable UUID id) {
        BureauJob job   = bureauService.findById(id);
        List<BureauJobItem> items = bureauService.listItems(id);
        return ResponseEntity.ok(ApiResponse.ok(new BureauJobDetail(job, items)));
    }

    // ── Lifecycle Commands ────────────────────────────────────────────────────

    /**
     * Submit a PENDING job to the bureau.
     * Generates CDP data for each card, stores integrity hashes, marks the job SENT.
     */
    @PostMapping("/jobs/{id}/submit")
    public ResponseEntity<ApiResponse<BureauJob>> submitJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(bureauService.submitJob(id)));
    }

    /**
     * Process a bureau confirmation callback.
     * Marks personalised cards as PRODUCED; closes the job if no items remain pending.
     */
    @PostMapping("/jobs/{id}/confirm")
    public ResponseEntity<ApiResponse<BureauJob>> confirmJob(
            @PathVariable UUID id,
            @RequestBody @Valid BureauConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(bureauService.confirmJob(id, request)));
    }

    /**
     * Mark all PRODUCED cards in a job as DISPATCHED (bureau has shipped the cards).
     */
    @PostMapping("/jobs/{id}/dispatch")
    public ResponseEntity<ApiResponse<BureauJob>> dispatchJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(bureauService.dispatchJob(id)));
    }

    /**
     * Mark a job as FAILED (transmission error or bureau rejection).
     */
    @PostMapping("/jobs/{id}/fail")
    public ResponseEntity<ApiResponse<BureauJob>> failJob(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Manual failure marking") String reason) {
        return ResponseEntity.ok(ApiResponse.ok(bureauService.failJob(id, reason)));
    }

    // ── Dev / Ops Tools ───────────────────────────────────────────────────────

    /**
     * Preview the CDP record that would be generated for a specific card in a job.
     * Dev/ops diagnostic — shows the scheme AID, service code, IACs, and integrity hash.
     * Does NOT expose the encrypted PAN in the response body.
     */
    @GetMapping("/jobs/{jobId}/cdp/{cardId}")
    public ResponseEntity<ApiResponse<CdpPreviewResponse>> previewCdp(
            @PathVariable UUID jobId,
            @PathVariable UUID cardId) {
        CdpRecord cdp = bureauService.generateCdpPreview(jobId, cardId);
        // Strip the encrypted PAN from the preview response — never expose it via REST
        CdpPreviewResponse preview = new CdpPreviewResponse(
                cdp.cardId(), cdp.schemeAid(), cdp.schemeLabel(),
                cdp.expiryYYMM(), cdp.sequenceNo(), cdp.serviceCode(),
                cdp.aip(), cdp.iacDefault(), cdp.iacDenial(), cdp.iacOnline(),
                cdp.pdol(), cdp.issuerKeyIndex(), cdp.cvkIndex(), cdp.hash()
        );
        return ResponseEntity.ok(ApiResponse.ok(preview));
    }

    // ── Nested response types ─────────────────────────────────────────────────

    /** Full job detail including item list. */
    public record BureauJobDetail(BureauJob job, List<BureauJobItem> items) {}

    /**
     * CDP preview safe for REST responses — encrypted PAN stripped out.
     * All fields mirror {@link CdpRecord} except {@code panEncryptedForBureau}.
     */
    public record CdpPreviewResponse(
            java.util.UUID cardId,
            String schemeAid, String schemeLabel,
            String expiryYYMM, int sequenceNo, String serviceCode,
            String aip, String iacDefault, String iacDenial, String iacOnline,
            String pdol, int issuerKeyIndex, int cvkIndex,
            String hash
    ) {}
}

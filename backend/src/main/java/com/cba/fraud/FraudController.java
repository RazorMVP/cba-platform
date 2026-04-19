package com.cba.fraud;

import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud & Risk Management", description = "Transaction velocity rules, blacklist, alerts, cases, risk scores")
public class FraudController {

    private final FraudAlertService     alertService;
    private final BlacklistService      blacklistService;
    private final FraudRuleRepository   ruleRepository;
    private final CustomerRiskScoreRepository riskScoreRepository;
    private final FraudEngineService    fraudEngineService;

    // ─── Rules ──────────────────────────────────────────────────────────────

    @GetMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List fraud rules")
    public ResponseEntity<ApiResponse<Page<FraudRule>>> listRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(ruleRepository.findAll(pageable)));
    }

    @GetMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get fraud rule by ID")
    public ResponseEntity<ApiResponse<FraudRule>> getRule(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
            ruleRepository.findById(id).orElseThrow(() ->
                CbaException.notFound("FraudRule", id))));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update fraud rule (enable/disable, weight, params)")
    public ResponseEntity<ApiResponse<FraudRule>> updateRule(
            @PathVariable UUID id, @RequestBody UpdateRuleRequest req) {
        FraudRule rule = ruleRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("FraudRule", id));
        if (req.enabled() != null)   rule.setEnabled(req.enabled());
        if (req.blocking() != null)  rule.setBlocking(req.blocking());
        if (req.severity() != null)  rule.setSeverity(req.severity());
        if (req.params() != null)    rule.setParams(req.params());
        if (req.description() != null) rule.setDescription(req.description());
        return ResponseEntity.ok(ApiResponse.ok(ruleRepository.save(rule)));
    }

    record UpdateRuleRequest(Boolean enabled, Boolean blocking, String severity,
                             String params, String description) {}

    // ─── Alerts ─────────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "List fraud alerts")
    public ResponseEntity<ApiResponse<Page<FraudAlert>>> listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            alertService.listAlerts(status, severity, customerId, PageRequest.of(page, size))));
    }

    @GetMapping("/alerts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Get fraud alert by ID")
    public ResponseEntity<ApiResponse<FraudAlert>> getAlert(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.getAlert(id)));
    }

    @PostMapping("/alerts/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark alert as under review")
    public ResponseEntity<ApiResponse<FraudAlert>> reviewAlert(
            @PathVariable UUID id, @RequestBody ReviewRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.reviewAlert(id, req.reviewedBy())));
    }

    @PostMapping("/alerts/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close alert (CLOSED_FALSE_POSITIVE, CLOSED_CONFIRMED, SUPPRESSED)")
    public ResponseEntity<ApiResponse<FraudAlert>> closeAlert(
            @PathVariable UUID id, @RequestBody CloseAlertRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
            alertService.closeAlert(id, req.status(), req.reviewedBy())));
    }

    record ReviewRequest(String reviewedBy) {}
    record CloseAlertRequest(String status, String reviewedBy) {}

    // ─── Cases ───────────────────────────────────────────────────────────────

    @GetMapping("/cases")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "List fraud cases")
    public ResponseEntity<ApiResponse<Page<FraudCase>>> listCases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            alertService.listCases(status, riskLevel, customerId, PageRequest.of(page, size))));
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Get fraud case by ID")
    public ResponseEntity<ApiResponse<FraudCase>> getCase(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.getCase(id)));
    }

    @PostMapping("/cases")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create fraud case")
    public ResponseEntity<ApiResponse<FraudCase>> createCase(@RequestBody CreateCaseRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.ok(
            alertService.createCase(req.title(), req.customerId(), req.riskLevel(), req.assignedTo())));
    }

    @PutMapping("/cases/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update fraud case (status, assignedTo, resolutionNotes)")
    public ResponseEntity<ApiResponse<FraudCase>> updateCase(
            @PathVariable UUID id, @RequestBody UpdateCaseRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
            alertService.updateCase(id, req.status(), req.assignedTo(), req.resolutionNotes())));
    }

    @PostMapping("/cases/{caseId}/alerts/{alertId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Link an alert to a fraud case")
    public ResponseEntity<ApiResponse<FraudCase>> linkAlert(
            @PathVariable UUID caseId, @PathVariable UUID alertId) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.linkAlertToCase(alertId, caseId)));
    }

    record CreateCaseRequest(String title, UUID customerId, String riskLevel, String assignedTo) {}
    record UpdateCaseRequest(String status, String assignedTo, String resolutionNotes) {}

    // ─── Blacklist ────────────────────────────────────────────────────────────

    @GetMapping("/blacklist")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "List blacklist entries")
    public ResponseEntity<ApiResponse<Page<BlacklistEntry>>> listBlacklist(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            blacklistService.listEntries(entityType, active, PageRequest.of(page, size))));
    }

    @GetMapping("/blacklist/search")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Search active blacklist entries by value")
    public ResponseEntity<ApiResponse<?>> searchBlacklist(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(blacklistService.search(q)));
    }

    @GetMapping("/blacklist/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Get blacklist entry by ID")
    public ResponseEntity<ApiResponse<BlacklistEntry>> getBlacklistEntry(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(blacklistService.getEntry(id)));
    }

    @PostMapping("/blacklist")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add entity to blacklist")
    public ResponseEntity<ApiResponse<BlacklistEntry>> addToBlacklist(
            @RequestBody AddBlacklistRequest req) {
        Instant expiresAt = req.expiresAt() != null ? Instant.parse(req.expiresAt()) : null;
        return ResponseEntity.status(201).body(ApiResponse.ok(
            blacklistService.addEntry(req.entityType(), req.entityValue(), req.reason(),
                req.source(), expiresAt, req.addedBy())));
    }

    @PutMapping("/blacklist/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update blacklist entry (reason, expiresAt)")
    public ResponseEntity<ApiResponse<BlacklistEntry>> updateBlacklistEntry(
            @PathVariable UUID id, @RequestBody UpdateBlacklistRequest req) {
        Instant expiresAt = req.expiresAt() != null ? Instant.parse(req.expiresAt()) : null;
        return ResponseEntity.ok(ApiResponse.ok(
            blacklistService.updateEntry(id, req.reason(), expiresAt)));
    }

    @DeleteMapping("/blacklist/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate blacklist entry")
    public ResponseEntity<ApiResponse<BlacklistEntry>> deactivateBlacklistEntry(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(blacklistService.deactivateEntry(id)));
    }

    record AddBlacklistRequest(String entityType, String entityValue, String reason,
                               String source, String expiresAt, String addedBy) {}
    record UpdateBlacklistRequest(String reason, String expiresAt) {}

    // ─── Risk Scores ──────────────────────────────────────────────────────────

    @GetMapping("/risk-scores")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List customer risk scores")
    public ResponseEntity<ApiResponse<Page<CustomerRiskScore>>> listRiskScores(
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CustomerRiskScore> result = riskLevel != null
            ? riskScoreRepository.findByRiskLevelOrderByScoreDesc(riskLevel, pageable)
            : riskScoreRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/risk-scores/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Get risk score for a customer")
    public ResponseEntity<ApiResponse<CustomerRiskScore>> getRiskScore(@PathVariable UUID customerId) {
        CustomerRiskScore score = riskScoreRepository.findByCustomerId(customerId)
            .orElseGet(() -> {
                CustomerRiskScore s = new CustomerRiskScore();
                s.setCustomerId(customerId);
                return s;
            });
        return ResponseEntity.ok(ApiResponse.ok(score));
    }

    @PostMapping("/risk-scores/{customerId}/recalculate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manually trigger risk score recalculation")
    public ResponseEntity<ApiResponse<String>> recalculate(@PathVariable UUID customerId) {
        fraudEngineService.recalculateRiskScore(customerId);
        return ResponseEntity.ok(ApiResponse.ok("Risk score recalculation triggered"));
    }
}

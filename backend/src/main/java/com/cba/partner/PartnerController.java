package com.cba.partner;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    // ── Public ────────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody RegisterRequest req) {
        partnerService.register(req.organizationName(), req.email(), req.password());
        return ResponseEntity.status(201).body(ApiResponse.ok(null));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<PartnerService.LoginResult>> login(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(partnerService.login(req.email(), req.password())));
    }

    // ── API Keys ──────────────────────────────────────────────────────────────

    @GetMapping("/{orgId}/api-keys")
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> listApiKeys(@PathVariable UUID orgId) {
        List<ApiKeyResponse> keys = partnerService.listApiKeys(orgId).stream()
                .map(k -> new ApiKeyResponse(
                        k.getId().toString(),
                        k.getName(),
                        k.getKeyPrefix(),
                        k.getScopes(),
                        k.getTier(),
                        k.getLastUsedAt() != null ? k.getLastUsedAt().toString() : null,
                        k.getCreatedAt() != null ? k.getCreatedAt().toString() : null,
                        k.isActive()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(keys));
    }

    @PostMapping("/{orgId}/api-keys")
    public ResponseEntity<ApiResponse<Map<String, String>>> issueApiKey(@PathVariable UUID orgId, @RequestBody IssueKeyRequest req) {
        String key = partnerService.issueApiKey(orgId, req.name(), req.scopes());
        return ResponseEntity.status(201).body(ApiResponse.ok(Map.of("key", key)));
    }

    @DeleteMapping("/{orgId}/api-keys/{keyId}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@PathVariable UUID orgId, @PathVariable UUID keyId) {
        partnerService.revokeApiKey(orgId, keyId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Production Application ────────────────────────────────────────────────

    @PostMapping("/{orgId}/applications")
    public ResponseEntity<ApiResponse<Void>> submitApplication(@PathVariable UUID orgId, @RequestBody PartnerApplicationRequest req) {
        partnerService.submitApplication(orgId, req);
        return ResponseEntity.status(201).body(ApiResponse.ok(null));
    }

    // ── Usage (stub — returns empty placeholder) ──────────────────────────────

    @GetMapping("/{orgId}/usage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUsage(@PathVariable UUID orgId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "totalRequests", 0,
                "successRequests", 0,
                "failedRequests", 0,
                "webhookDeliveryRate", 0,
                "dailyCalls", List.of(),
                "topEndpoints", List.of()
        )));
    }

    // ── Admin — list all partners ─────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<OrgResponse>>> listAll() {
        List<OrgResponse> orgs = partnerService.listAll().stream()
                .map(o -> new OrgResponse(
                        o.getId().toString(),
                        o.getName(),
                        o.getStatus().name(),
                        o.getTier(),
                        o.getEnvironment().name(),
                        o.getCreatedAt() != null ? o.getCreatedAt().toString() : null,
                        0))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(orgs));
    }

    @GetMapping("/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllUsage(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(List.of()));
    }

    @PostMapping("/{orgId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable UUID orgId, Principal principal) {
        partnerService.approveProduction(orgId, principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{orgId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable UUID orgId) {
        partnerService.rejectApplication(orgId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Request / Response records ────────────────────────────────────────────

    public record RegisterRequest(String organizationName, String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record IssueKeyRequest(String name, List<String> scopes) {}

    public record ApiKeyResponse(
            String id, String name, String keyPrefix, List<String> scopes,
            String tier, String lastUsedAt, String createdAt, boolean active) {}

    public record OrgResponse(
            String id, String organizationName, String status, String tier,
            String environment, String createdAt, long totalApiCalls) {}
}

package com.cba.system;

import com.cba.account.algorithm.AccountNumberAlgorithmService;
import com.cba.account.algorithm.TenantAlgorithmConfig;
import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Manages per-tenant account number algorithm configuration.
 *
 * <p>GET  /api/v1/tenants/{id}/account-algorithm — retrieve current config
 * <p>PUT  /api/v1/tenants/{id}/account-algorithm — update config
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/account-algorithm")
@RequiredArgsConstructor
@Tag(name = "Account Number Algorithms", description = "Per-tenant account number algorithm configuration")
public class AccountAlgorithmController {

    private final AccountNumberAlgorithmService algorithmService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get algorithm config for a tenant")
    public ResponseEntity<ApiResponse<TenantAlgorithmConfig>> getConfig(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(algorithmService.getConfig(tenantId)));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update algorithm config for a tenant",
               description = "Sets which account number algorithm to use per account type. "
                           + "Requires bankCode when any type uses NUBAN.")
    public ResponseEntity<ApiResponse<TenantAlgorithmConfig>> updateConfig(
            @PathVariable UUID tenantId,
            @RequestBody TenantAlgorithmConfig config) {
        return ResponseEntity.ok(ApiResponse.ok(algorithmService.updateConfig(tenantId, config)));
    }
}

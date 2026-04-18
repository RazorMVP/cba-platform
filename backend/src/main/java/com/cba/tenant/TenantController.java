package com.cba.tenant;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tenants", description = "Multi-tenant configuration — list registered tenants with their base currency, account number algorithm settings and country parameters")
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;

    @Operation(summary = "List all registered tenants (ADMIN only)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Tenant>>> listTenants() {
        List<Tenant> tenants = tenantRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok(tenants));
    }
}

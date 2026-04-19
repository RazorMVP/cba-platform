package com.cba.system;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Global Configuration", description = "Platform-wide key-value settings — toggle feature flags, set thresholds and adjust system behaviour")
@RestController
@RequestMapping("/api/v1/configurations")
@RequiredArgsConstructor
public class GlobalConfigController {

    private final SystemConfigService service;

    @Operation(summary = "List all global configuration entries")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<GlobalConfiguration>> list() {
        return ApiResponse.ok(service.listConfigs());
    }

    @Operation(summary = "Get a global configuration entry by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GlobalConfiguration> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getConfig(id));
    }

    @Operation(summary = "Update a global configuration entry")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GlobalConfiguration> update(
            @PathVariable UUID id,
            @RequestBody SystemConfigService.UpdateGlobalConfigRequest req) {
        return ApiResponse.ok(service.updateConfig(id, req));
    }
}

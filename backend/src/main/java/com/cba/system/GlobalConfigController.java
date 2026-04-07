package com.cba.system;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/configurations")
@RequiredArgsConstructor
public class GlobalConfigController {

    private final SystemConfigService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<GlobalConfiguration>> list() {
        return ApiResponse.ok(service.listConfigs());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GlobalConfiguration> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getConfig(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GlobalConfiguration> update(
            @PathVariable UUID id,
            @RequestBody SystemConfigService.UpdateGlobalConfigRequest req) {
        return ApiResponse.ok(service.updateConfig(id, req));
    }
}

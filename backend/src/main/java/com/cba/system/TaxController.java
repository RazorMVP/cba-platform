package com.cba.system;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService service;

    // ── Tax Components ────────────────────────────────────────────────

    @GetMapping("/taxes/components")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<TaxComponent>> listComponents(Pageable pageable) {
        return ApiResponse.ok(service.listComponents(pageable));
    }

    @GetMapping("/taxes/components/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TaxComponent> getComponent(@PathVariable UUID id) {
        return ApiResponse.ok(service.getComponent(id));
    }

    @PostMapping("/taxes/components")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TaxComponent> createComponent(@RequestBody TaxService.CreateTaxComponentRequest req) {
        return ApiResponse.ok(service.createComponent(req));
    }

    @PutMapping("/taxes/components/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TaxComponent> updateComponent(
            @PathVariable UUID id,
            @RequestBody TaxService.CreateTaxComponentRequest req) {
        return ApiResponse.ok(service.updateComponent(id, req));
    }

    @DeleteMapping("/taxes/components/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteComponent(@PathVariable UUID id) {
        service.deleteComponent(id);
    }

    // ── Tax Groups ────────────────────────────────────────────────────

    @GetMapping("/taxes/groups")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<TaxGroup>> listGroups(Pageable pageable) {
        return ApiResponse.ok(service.listGroups(pageable));
    }

    @GetMapping("/taxes/groups/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TaxGroup> getGroup(@PathVariable UUID id) {
        return ApiResponse.ok(service.getGroup(id));
    }

    @PostMapping("/taxes/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TaxGroup> createGroup(@RequestBody TaxService.CreateTaxGroupRequest req) {
        return ApiResponse.ok(service.createGroup(req));
    }

    @PutMapping("/taxes/groups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TaxGroup> updateGroup(
            @PathVariable UUID id,
            @RequestBody TaxService.CreateTaxGroupRequest req) {
        return ApiResponse.ok(service.updateGroup(id, req));
    }

    @DeleteMapping("/taxes/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteGroup(@PathVariable UUID id) {
        service.deleteGroup(id);
    }
}

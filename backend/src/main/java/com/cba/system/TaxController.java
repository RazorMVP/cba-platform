package com.cba.system;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Taxes", description = "Tax components and tax groups — define withholding tax rates and bundle them into groups for application on interest income")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService service;

    // ── Tax Components ────────────────────────────────────────────────

    @Operation(summary = "List all tax components")
    @GetMapping("/taxes/components")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<TaxComponent>> listComponents(Pageable pageable) {
        return ApiResponse.ok(service.listComponents(pageable));
    }

    @Operation(summary = "Get a tax component by ID")
    @GetMapping("/taxes/components/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TaxComponent> getComponent(@PathVariable UUID id) {
        return ApiResponse.ok(service.getComponent(id));
    }

    @Operation(summary = "Create a new tax component")
    @PostMapping("/taxes/components")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TaxComponent> createComponent(@RequestBody TaxService.CreateTaxComponentRequest req) {
        return ApiResponse.ok(service.createComponent(req));
    }

    @Operation(summary = "Update a tax component")
    @PutMapping("/taxes/components/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TaxComponent> updateComponent(
            @PathVariable UUID id,
            @RequestBody TaxService.CreateTaxComponentRequest req) {
        return ApiResponse.ok(service.updateComponent(id, req));
    }

    @Operation(summary = "Delete a tax component")
    @DeleteMapping("/taxes/components/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteComponent(@PathVariable UUID id) {
        service.deleteComponent(id);
    }

    // ── Tax Groups ────────────────────────────────────────────────────

    @Operation(summary = "List all tax groups")
    @GetMapping("/taxes/groups")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<TaxGroup>> listGroups(Pageable pageable) {
        return ApiResponse.ok(service.listGroups(pageable));
    }

    @Operation(summary = "Get a tax group by ID")
    @GetMapping("/taxes/groups/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TaxGroup> getGroup(@PathVariable UUID id) {
        return ApiResponse.ok(service.getGroup(id));
    }

    @Operation(summary = "Create a new tax group")
    @PostMapping("/taxes/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TaxGroup> createGroup(@RequestBody TaxService.CreateTaxGroupRequest req) {
        return ApiResponse.ok(service.createGroup(req));
    }

    @Operation(summary = "Update a tax group")
    @PutMapping("/taxes/groups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TaxGroup> updateGroup(
            @PathVariable UUID id,
            @RequestBody TaxService.CreateTaxGroupRequest req) {
        return ApiResponse.ok(service.updateGroup(id, req));
    }

    @Operation(summary = "Delete a tax group")
    @DeleteMapping("/taxes/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteGroup(@PathVariable UUID id) {
        service.deleteGroup(id);
    }
}

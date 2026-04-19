package com.cba.accounting;

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

@Tag(name = "Provisioning Criteria", description = "Loan loss provisioning definitions — IFRS 9/Basel II age-band categories with provision percentages and GL account linkages")
@RestController
@RequestMapping("/api/v1/provisioningcriteria")
@RequiredArgsConstructor
public class ProvisioningCriteriaController {

    private final ProvisioningCriteriaService provisioningCriteriaService;

    @Operation(summary = "List all provisioning criteria")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<ProvisioningCriteria>> list(Pageable pageable) {
        return ApiResponse.ok(provisioningCriteriaService.listCriteria(pageable));
    }

    @Operation(summary = "Get a provisioning criteria by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ProvisioningCriteria> get(@PathVariable UUID id) {
        return ApiResponse.ok(provisioningCriteriaService.getCriteria(id));
    }

    @Operation(summary = "Create a new provisioning criteria with age-band definitions")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProvisioningCriteria> create(
            @RequestBody ProvisioningCriteriaService.CreateCriteriaRequest req) {
        return ApiResponse.ok(provisioningCriteriaService.createCriteria(req));
    }

    @Operation(summary = "Update a provisioning criteria and replace all age-band definitions")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProvisioningCriteria> update(@PathVariable UUID id,
            @RequestBody ProvisioningCriteriaService.CreateCriteriaRequest req) {
        return ApiResponse.ok(provisioningCriteriaService.updateCriteria(id, req));
    }

    @Operation(summary = "Delete a provisioning criteria")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        provisioningCriteriaService.deleteCriteria(id);
    }
}

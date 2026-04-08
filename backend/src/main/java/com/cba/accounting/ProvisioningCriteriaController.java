package com.cba.accounting;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/provisioningcriteria")
@RequiredArgsConstructor
public class ProvisioningCriteriaController {

    private final ProvisioningCriteriaService provisioningCriteriaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<ProvisioningCriteria>> list(Pageable pageable) {
        return ApiResponse.ok(provisioningCriteriaService.listCriteria(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ProvisioningCriteria> get(@PathVariable UUID id) {
        return ApiResponse.ok(provisioningCriteriaService.getCriteria(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProvisioningCriteria> create(
            @RequestBody ProvisioningCriteriaService.CreateCriteriaRequest req) {
        return ApiResponse.ok(provisioningCriteriaService.createCriteria(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProvisioningCriteria> update(@PathVariable UUID id,
            @RequestBody ProvisioningCriteriaService.CreateCriteriaRequest req) {
        return ApiResponse.ok(provisioningCriteriaService.updateCriteria(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        provisioningCriteriaService.deleteCriteria(id);
    }
}

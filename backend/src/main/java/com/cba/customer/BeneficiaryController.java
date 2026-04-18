package com.cba.customer;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Client Beneficiaries", description = "Third-party transfer beneficiaries per customer — add, update, list and soft-delete trusted payees with optional transfer limits")
@RestController
@RequestMapping("/api/v1/clients/{customerId}/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @Operation(summary = "List active beneficiaries for a customer")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<List<Beneficiary>> list(@PathVariable UUID customerId) {
        return ApiResponse.ok(beneficiaryService.listBeneficiaries(customerId));
    }

    @Operation(summary = "Get a single beneficiary — returns 404 if customerId does not match (prevents enumeration)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<Beneficiary> get(@PathVariable UUID customerId, @PathVariable UUID id) {
        return ApiResponse.ok(beneficiaryService.getBeneficiary(customerId, id));
    }

    @Operation(summary = "Add a new beneficiary for a customer")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<Beneficiary> create(@PathVariable UUID customerId,
            @RequestBody BeneficiaryService.CreateBeneficiaryRequest req) {
        return ApiResponse.ok(beneficiaryService.createBeneficiary(customerId, req));
    }

    @Operation(summary = "Update a beneficiary's details or transfer limit")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<Beneficiary> update(@PathVariable UUID customerId, @PathVariable UUID id,
            @RequestBody BeneficiaryService.CreateBeneficiaryRequest req) {
        return ApiResponse.ok(beneficiaryService.updateBeneficiary(customerId, id, req));
    }

    @Operation(summary = "Soft-delete (deactivate) a beneficiary")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public void delete(@PathVariable UUID customerId, @PathVariable UUID id) {
        beneficiaryService.deactivate(customerId, id);
    }
}

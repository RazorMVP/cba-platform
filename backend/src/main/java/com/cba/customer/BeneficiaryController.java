package com.cba.customer;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{customerId}/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<List<Beneficiary>> list(@PathVariable UUID customerId) {
        return ApiResponse.ok(beneficiaryService.listBeneficiaries(customerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<Beneficiary> get(@PathVariable UUID customerId, @PathVariable UUID id) {
        return ApiResponse.ok(beneficiaryService.getBeneficiary(customerId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<Beneficiary> create(@PathVariable UUID customerId,
            @RequestBody BeneficiaryService.CreateBeneficiaryRequest req) {
        return ApiResponse.ok(beneficiaryService.createBeneficiary(customerId, req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<Beneficiary> update(@PathVariable UUID customerId, @PathVariable UUID id,
            @RequestBody BeneficiaryService.CreateBeneficiaryRequest req) {
        return ApiResponse.ok(beneficiaryService.updateBeneficiary(customerId, id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public void delete(@PathVariable UUID customerId, @PathVariable UUID id) {
        beneficiaryService.deactivate(customerId, id);
    }
}

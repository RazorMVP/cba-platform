package com.cba.charge;

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

@Tag(name = "Charges", description = "Charge definition catalogue — fee templates applied to loans, accounts or clients at disbursement, due date or instalment")
@RestController
@RequestMapping("/api/v1/charges")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;

    @Operation(summary = "List charge definitions (optionally filtered by ?appliesTo=LOAN|SAVINGS|CLIENT)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ChargeDefinition>> list(
            @RequestParam(required = false) ChargeDefinition.ChargeAppliesTo appliesTo,
            Pageable pageable) {
        return ApiResponse.ok(chargeService.listCharges(appliesTo, pageable));
    }

    @Operation(summary = "Get a charge definition by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ChargeDefinition> get(@PathVariable UUID id) {
        return ApiResponse.ok(chargeService.getCharge(id));
    }

    @Operation(summary = "Create a new charge definition")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ChargeDefinition> create(@RequestBody ChargeService.CreateChargeRequest req) {
        return ApiResponse.ok(chargeService.createCharge(req));
    }

    @Operation(summary = "Update a charge definition")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ChargeDefinition> update(@PathVariable UUID id, @RequestBody ChargeService.CreateChargeRequest req) {
        return ApiResponse.ok(chargeService.updateCharge(id, req));
    }

    @Operation(summary = "Delete a charge definition")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        chargeService.deleteCharge(id);
    }
}

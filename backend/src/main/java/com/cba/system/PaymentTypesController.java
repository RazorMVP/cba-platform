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

@Tag(name = "Payment Types", description = "Payment method catalogue — cash and non-cash payment types displayed in transaction forms")
@RestController
@RequestMapping("/api/v1/paymenttypes")
@RequiredArgsConstructor
public class PaymentTypesController {

    private final SystemConfigService service;

    @Operation(summary = "List all payment types")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<SystemPaymentType>> list(Pageable pageable) {
        return ApiResponse.ok(service.listPaymentTypes(pageable));
    }

    @Operation(summary = "Get a payment type by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SystemPaymentType> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getPaymentType(id));
    }

    @Operation(summary = "Create a new payment type")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPaymentType> create(@RequestBody SystemConfigService.CreatePaymentTypeRequest req) {
        return ApiResponse.ok(service.createPaymentType(req));
    }

    @Operation(summary = "Update a payment type")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPaymentType> update(
            @PathVariable UUID id,
            @RequestBody SystemConfigService.CreatePaymentTypeRequest req) {
        return ApiResponse.ok(service.updatePaymentType(id, req));
    }

    @Operation(summary = "Delete a payment type (system-defined types cannot be deleted)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deletePaymentType(id);
    }
}

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
@RequestMapping("/api/v1/paymenttypes")
@RequiredArgsConstructor
public class PaymentTypesController {

    private final SystemConfigService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<SystemPaymentType>> list(Pageable pageable) {
        return ApiResponse.ok(service.listPaymentTypes(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SystemPaymentType> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getPaymentType(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPaymentType> create(@RequestBody SystemConfigService.CreatePaymentTypeRequest req) {
        return ApiResponse.ok(service.createPaymentType(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPaymentType> update(
            @PathVariable UUID id,
            @RequestBody SystemConfigService.CreatePaymentTypeRequest req) {
        return ApiResponse.ok(service.updatePaymentType(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.deletePaymentType(id);
    }
}

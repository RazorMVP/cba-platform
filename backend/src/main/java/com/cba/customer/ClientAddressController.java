package com.cba.customer;

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

@Tag(name = "Client Addresses", description = "Customer address records — HOME, WORK and MAILING address types with full postal fields and country code")
@RestController
@RequestMapping("/api/v1/clients/{customerId}/addresses")
@RequiredArgsConstructor
public class ClientAddressController {

    private final ClientExtensionService service;

    @Operation(summary = "List all addresses for a customer")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ClientAddress>> list(@PathVariable UUID customerId, Pageable pageable) {
        return ApiResponse.ok(service.listAddresses(customerId, pageable));
    }

    @Operation(summary = "Add a new address for a customer")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientAddress> create(
            @PathVariable UUID customerId,
            @RequestBody ClientExtensionService.CreateAddressRequest req) {
        return ApiResponse.ok(service.createAddress(customerId, req));
    }

    @Operation(summary = "Delete a customer address by ID")
    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        service.deleteAddress(customerId, addressId);
    }
}

package com.cba.customer;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients/{customerId}/addresses")
@RequiredArgsConstructor
public class ClientAddressController {

    private final ClientExtensionService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ClientAddress>> list(@PathVariable UUID customerId, Pageable pageable) {
        return ApiResponse.ok(service.listAddresses(customerId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientAddress> create(
            @PathVariable UUID customerId,
            @RequestBody ClientExtensionService.CreateAddressRequest req) {
        return ApiResponse.ok(service.createAddress(customerId, req));
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        service.deleteAddress(customerId, addressId);
    }
}

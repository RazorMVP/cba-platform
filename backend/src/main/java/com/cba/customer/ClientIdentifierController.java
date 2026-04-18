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

@Tag(name = "Client Identifiers", description = "Customer identity documents — passport, national ID, driver's licence and other KYC document types with expiry tracking")
@RestController
@RequestMapping("/api/v1/clients/{customerId}/identifiers")
@RequiredArgsConstructor
public class ClientIdentifierController {

    private final ClientExtensionService service;

    @Operation(summary = "List all identity documents for a customer")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ClientIdentifier>> list(@PathVariable UUID customerId, Pageable pageable) {
        return ApiResponse.ok(service.listIdentifiers(customerId, pageable));
    }

    @Operation(summary = "Add a new identity document for a customer")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientIdentifier> create(
            @PathVariable UUID customerId,
            @RequestBody ClientExtensionService.CreateIdentifierRequest req) {
        return ApiResponse.ok(service.createIdentifier(customerId, req));
    }

    @Operation(summary = "Delete a customer identity document by ID")
    @DeleteMapping("/{identifierId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID customerId, @PathVariable UUID identifierId) {
        service.deleteIdentifier(customerId, identifierId);
    }
}

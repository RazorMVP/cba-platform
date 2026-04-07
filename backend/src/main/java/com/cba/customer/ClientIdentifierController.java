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
@RequestMapping("/api/v1/clients/{customerId}/identifiers")
@RequiredArgsConstructor
public class ClientIdentifierController {

    private final ClientExtensionService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ClientIdentifier>> list(@PathVariable UUID customerId, Pageable pageable) {
        return ApiResponse.ok(service.listIdentifiers(customerId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientIdentifier> create(
            @PathVariable UUID customerId,
            @RequestBody ClientExtensionService.CreateIdentifierRequest req) {
        return ApiResponse.ok(service.createIdentifier(customerId, req));
    }

    @DeleteMapping("/{identifierId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID customerId, @PathVariable UUID identifierId) {
        service.deleteIdentifier(customerId, identifierId);
    }
}

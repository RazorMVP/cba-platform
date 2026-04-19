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

@Tag(name = "Client Charges", description = "Charges applied directly to a client (customer) — add and waive client-level fee instances")
@RestController
@RequestMapping("/api/v1/clients/{customerId}/charges")
@RequiredArgsConstructor
public class ClientChargeController {

    private final ChargeService chargeService;

    @Operation(summary = "List charges applied to a client")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ClientCharge>> list(@PathVariable UUID customerId, Pageable pageable) {
        return ApiResponse.ok(chargeService.getClientCharges(customerId, pageable));
    }

    @Operation(summary = "Apply a charge definition to a client")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientCharge> add(@PathVariable UUID customerId, @RequestBody ChargeService.AddChargeRequest req) {
        return ApiResponse.ok(chargeService.addClientCharge(customerId, req));
    }

    @Operation(summary = "Waive a charge on a client")
    @PostMapping("/{chargeId}/waive")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<ClientCharge> waive(@PathVariable UUID customerId, @PathVariable UUID chargeId) {
        return ApiResponse.ok(chargeService.waiveClientCharge(customerId, chargeId));
    }
}

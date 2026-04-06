package com.cba.openbanking;

import com.cba.openbanking.dto.FundsConfirmationRequest;
import com.cba.openbanking.dto.FundsConfirmationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * FAPI 2.0 compliant Card-Based Payment Instrument Issuer (CBPII) endpoints.
 * Allows TPPs to confirm whether sufficient funds are available in an account.
 */
@RestController
@RequestMapping("/open-banking/v3.1/cbpii")
@RequiredArgsConstructor
@Tag(name = "Open Banking — CBPII", description = "UK Open Banking v3.1 Funds Confirmation")
@SecurityRequirement(name = "oauth2")
public class CbpiiController {

    private final ConsentService consentService;

    @PostMapping("/funds-confirmations")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Confirm funds availability for a given account and amount (CBPII)")
    public ResponseEntity<FundsConfirmationResponse> confirmFunds(
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @Valid @RequestBody FundsConfirmationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(consentService.confirmFunds(request));
    }
}

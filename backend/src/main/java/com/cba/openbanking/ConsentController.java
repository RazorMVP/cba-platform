package com.cba.openbanking;

import com.cba.openbanking.dto.ConsentRequest;
import com.cba.openbanking.dto.ConsentResponse;
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
 * Open Banking consent lifecycle endpoints.
 * AWAITING_AUTHORISATION → AUTHORISED → REVOKED
 */
@RestController
@RequestMapping("/open-banking/v3.1/consents")
@RequiredArgsConstructor
@Tag(name = "Open Banking — Consents", description = "TPP consent creation and lifecycle management")
@SecurityRequirement(name = "oauth2")
public class ConsentController {

    private final ConsentService consentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT', 'ADMIN')")
    @Operation(summary = "Create a new consent (TPP access request)")
    public ResponseEntity<ConsentResponse> createConsent(
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @Valid @RequestBody ConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consentService.createConsent(request));
    }

    @GetMapping("/{consentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT', 'ADMIN')")
    @Operation(summary = "Get consent details by consentId")
    public ResponseEntity<ConsentResponse> getConsent(
            @PathVariable String consentId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId) {
        return ResponseEntity.ok(consentService.getConsent(consentId));
    }

    @PutMapping("/{consentId}/authorise")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Authorise a consent (customer approval step)")
    public ResponseEntity<ConsentResponse> authoriseConsent(
            @PathVariable String consentId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId) {
        return ResponseEntity.ok(consentService.authoriseConsent(consentId));
    }

    @DeleteMapping("/{consentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT', 'ADMIN')")
    @Operation(summary = "Revoke a consent")
    public ResponseEntity<ConsentResponse> revokeConsent(
            @PathVariable String consentId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId) {
        return ResponseEntity.ok(consentService.revokeConsent(consentId));
    }
}

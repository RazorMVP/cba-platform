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
    @PreAuthorize("hasRole('API_CLIENT')")
    @Operation(summary = "Create a new consent (TPP access request)",
            description = "Partner API key only. The consent belongs to the key's organisation; scopes must be "
                    + "ConsentScope values (400 INVALID_SCOPE otherwise).")
    public ResponseEntity<ConsentResponse> createConsent(
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @Valid @RequestBody ConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consentService.createConsent(request));
    }

    @GetMapping("/{consentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Get consent details by consentId",
            description = "The consent's TPP or its customer only; anyone else gets 404.")
    public ResponseEntity<ConsentResponse> getConsent(
            @PathVariable String consentId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId) {
        return ResponseEntity.ok(consentService.getConsent(consentId));
    }

    @PutMapping("/{consentId}/authorise")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Authorise a consent (customer approval step)",
            description = "Only the customer the consent names; anyone else gets 404.")
    public ResponseEntity<ConsentResponse> authoriseConsent(
            @PathVariable String consentId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId) {
        return ResponseEntity.ok(consentService.authoriseConsent(consentId));
    }

    @DeleteMapping("/{consentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Revoke a consent",
            description = "The consent's TPP or its customer only; anyone else gets 404.")
    public ResponseEntity<ConsentResponse> revokeConsent(
            @PathVariable String consentId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId) {
        return ResponseEntity.ok(consentService.revokeConsent(consentId));
    }
}

package com.cba.openbanking;

import com.cba.common.response.ApiResponse;
import com.cba.openbanking.dto.StaffConsentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Bank staff's Open Banking consents screen. {@code /open-banking/**} serves customers and TPPs only,
 * so staff read and revoke consents here.
 */
@RestController
@RequestMapping("/api/v1/open-banking/consents")
@RequiredArgsConstructor
@Tag(name = "Open Banking — Staff", description = "Consents as bank staff see them: list, view, revoke for a customer")
@SecurityRequirement(name = "oauth2")
public class StaffConsentController {

    private final StaffConsentService staffConsentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "List consents",
            description = "Optional filters: customerId, tppClientId (the partner organisation id), status, "
                    + "type (AISP | PISP | CBPII). Newest first by default.")
    public ResponseEntity<ApiResponse<Page<StaffConsentResponse>>> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String tppClientId,
            @RequestParam(required = false) ConsentStatus status,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StaffConsentResponse> page = staffConsentService.list(customerId, tppClientId, status, type, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
                ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    @GetMapping("/{consentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Get a consent by its consentId (ob-…)")
    public ResponseEntity<ApiResponse<StaffConsentResponse>> get(@PathVariable String consentId) {
        return ResponseEntity.ok(ApiResponse.ok(staffConsentService.get(consentId)));
    }

    @PostMapping("/{consentId}/revoke")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Revoke a consent on the customer's behalf",
            description = "Needs a reason (audited). The TPP gets CONSENT.REVOKED. 409 if already revoked or consumed.")
    public ResponseEntity<ApiResponse<StaffConsentResponse>> revoke(@PathVariable String consentId,
                                                                    @Valid @RequestBody RevokeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(staffConsentService.revoke(consentId, request.reason())));
    }

    public record RevokeRequest(@NotBlank @Size(max = 500) String reason) {}
}

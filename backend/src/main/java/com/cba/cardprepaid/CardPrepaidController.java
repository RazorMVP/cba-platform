package com.cba.cardprepaid;

import com.cba.cardprepaid.dto.PrepaidClearingRequest;
import com.cba.cardprepaid.dto.PrepaidMovementResult;
import com.cba.cardprepaid.dto.PrepaidRefundRequest;
import com.cba.cardprepaid.dto.PrepaidTransferRequest;
import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prepaid card wallet movements (backlog 15c) — card-service only (service role CARD_SERVICE). GL 2500 and
 * 2300 take no manual journals: every prepaid posting comes from card-service, which owns the wallet balance.
 * A refusal is {@code approved: false} with a reason (200), never a 4xx.
 */
@RestController
@RequestMapping("/api/v1/card-prepaid")
@RequiredArgsConstructor
@Tag(name = "Prepaid Card Wallets", description = "Prepaid wallet loads, unloads, clearing and refunds (card-service)")
@SecurityRequirement(name = "oauth2")
public class CardPrepaidController {

    private final CardPrepaidService service;

    @PostMapping("/loads")
    @PreAuthorize("hasRole('CARD_SERVICE')")
    @Operation(summary = "Load a prepaid wallet from the customer's account (card-service)",
            description = "Debits accountId under the withdrawal rules: DR customer deposit / CR 2500 Prepaid Card Liabilities. "
                    + "The account must be the customer's (else ACCOUNT_NOT_FOUND) and in the wallet currency. Idempotent on "
                    + "reference. Reasons: REFERENCE_CONFLICT, ACCOUNT_NOT_FOUND, CURRENCY_MISMATCH, ACCOUNT_NOT_ACTIVE, "
                    + "ACCOUNT_IN_LOCKIN_PERIOD, INSUFFICIENT_FUNDS, INVALID_AMOUNT, UNKNOWN_CURRENCY.")
    public ResponseEntity<ApiResponse<PrepaidMovementResult>> load(@Valid @RequestBody PrepaidTransferRequest request,
                                                                   @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.load(request, actor(jwt))));
    }

    @PostMapping("/unloads")
    @PreAuthorize("hasRole('CARD_SERVICE')")
    @Operation(summary = "Redeem a prepaid wallet to the customer's account (card-service)",
            description = "Credits accountId: DR 2500 / CR customer deposit. Same rules and reasons as a load, without the funds check.")
    public ResponseEntity<ApiResponse<PrepaidMovementResult>> unload(@Valid @RequestBody PrepaidTransferRequest request,
                                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.unload(request, actor(jwt))));
    }

    @PostMapping("/clearings")
    @PreAuthorize("hasRole('CARD_SERVICE')")
    @Operation(summary = "Clear prepaid card spend (card-service batch close)",
            description = "DR 2500 / CR 2300 Card Scheme Settlement Payable, with interchange (EARNED: DR 2300 / CR 4005; "
                    + "PAID: DR 5007 / CR 2300) and scheme fees (DR 5006 / CR 2300) in the same journal. Idempotent on "
                    + "reference (a repeat returns the first figures). Reasons: REFERENCE_CONFLICT, INVALID_AMOUNT, "
                    + "UNKNOWN_CURRENCY, INVALID_CARD_FEES.")
    public ResponseEntity<ApiResponse<PrepaidMovementResult>> clearing(@Valid @RequestBody PrepaidClearingRequest request,
                                                                       @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.clearing(request, actor(jwt))));
    }

    @PostMapping("/clearings/{reference}/refund")
    @PreAuthorize("hasRole('CARD_SERVICE')")
    @Operation(summary = "Refund a cleared prepaid purchase (card-service reversal)",
            description = "The opposite of the clearing for amount (≤ cleared), interchange and fees pro rata (HALF_UP); once per "
                    + "clearing. Idempotent on refundReference. Reasons: CLEARING_NOT_FOUND, CURRENCY_MISMATCH, ALREADY_REFUNDED, "
                    + "AMOUNT_EXCEEDS_CLEARED, INVALID_AMOUNT, REFERENCE_CONFLICT.")
    public ResponseEntity<ApiResponse<PrepaidMovementResult>> refund(@PathVariable String reference,
                                                                     @Valid @RequestBody PrepaidRefundRequest request,
                                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(service.refund(reference, request, actor(jwt))));
    }

    private static String actor(Jwt jwt) {
        if (jwt == null) return "system";
        String user = jwt.getClaimAsString("preferred_username");
        return user != null ? user : jwt.getClaimAsString("azp");
    }
}

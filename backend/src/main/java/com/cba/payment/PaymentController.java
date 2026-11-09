package com.cba.payment;

import com.cba.common.response.ApiResponse;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.dto.ReversePaymentRequest;
import com.cba.payment.dto.StandingOrderRequest;
import com.cba.payment.dto.StandingOrderResponse;
import com.cba.payment.dto.ExternalPaymentOutcomeRequest;
import com.cba.payment.dto.ExternalPaymentRequest;
import com.cba.payment.dto.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Internal transfers and payment operations")
@SecurityRequirement(name = "oauth2")
public class PaymentController {

    private final PaymentService paymentService;
    private final ExternalPaymentOutcomeService outcomeService;

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER') or (hasRole('CUSTOMER') and @customerAccess.isAccountOwner(#request.sourceAccountId()))")
    @Operation(summary = "Execute an internal transfer between two accounts")
    public ResponseEntity<ApiResponse<PaymentResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(paymentService.transfer(request, actor)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER') or (hasRole('CUSTOMER') and @customerAccess.isPaymentParty(#id))")
    @Operation(summary = "Get payment details by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getPayment(id)));
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER') or (hasRole('CUSTOMER') and @customerAccess.isAccountOwner(#accountId))")
    @Operation(summary = "Get payment history for an account")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getAccountPayments(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PaymentResponse> page = paymentService.getAccountPayments(accountId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
            ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    // ── Standing Orders ──────────────────────────────────────────────────────

    @GetMapping("/standing-orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List standing orders for an account")
    public ResponseEntity<ApiResponse<List<StandingOrderResponse>>> listStandingOrders(
            @RequestParam UUID accountId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.listStandingOrders(accountId)));
    }

    @PostMapping("/standing-orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Create a recurring standing order")
    public ResponseEntity<ApiResponse<StandingOrderResponse>> createStandingOrder(
            @Valid @RequestBody StandingOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(paymentService.createStandingOrder(request)));
    }

    @DeleteMapping("/standing-orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Cancel a standing order")
    public ResponseEntity<ApiResponse<StandingOrderResponse>> cancelStandingOrder(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.cancelStandingOrder(id)));
    }

    // ── External / SWIFT / SEPA Payments ────────────────────────────────────

    @PostMapping("/external")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Initiate an external SWIFT or SEPA payment",
               description = "Debits the source account (withdrawal rules; DR deposit / CR 2400 Outgoing Payments " +
                             "in Transit) and submits it to the external payments gateway. The payment stays PROCESSING " +
                             "until the gateway's signed callback (or ops) settles or returns it. Source account must be " +
                             "in the payment currency (400 CURRENCY_MISMATCH).")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiateExternalPayment(
            @Valid @RequestBody ExternalPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(paymentService.initiateExternalPayment(request, actor)));
    }

    @GetMapping("/external")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all external payments (SWIFT/SEPA/ACH)")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> listExternalPayments(
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PaymentResponse> page = paymentService.listExternalPayments(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
                ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    @PostMapping("/{id}/external-outcome")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Record an external payment's outcome by hand (ops)",
               description = "For a PROCESSING external payment the gateway hasn't reported on: SETTLED posts "
                       + "DR 2400 / CR nostro; RETURNED credits the source back. Reason required; not by the user "
                       + "who initiated the payment (403 SAME_USER).")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordExternalOutcome(
            @PathVariable UUID id,
            @Valid @RequestBody ExternalPaymentOutcomeRequest request) {
        ExternalPaymentOutcomeService.Outcome outcome;
        try {
            outcome = ExternalPaymentOutcomeService.Outcome.valueOf(request.outcome().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw com.cba.common.exception.CbaException.badRequest("INVALID_OUTCOME", "outcome must be SETTLED or RETURNED");
        }
        return ResponseEntity.ok(ApiResponse.ok(outcomeService.applyManual(id, outcome, request.reason()).payment()));
    }

    // ── Payment Reversal ─────────────────────────────────────────────────────

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Reverse a completed payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> reversePayment(
            @PathVariable UUID id,
            @Valid @RequestBody ReversePaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(paymentService.reversePayment(id, request, actor)));
    }
}

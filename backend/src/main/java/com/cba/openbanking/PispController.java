package com.cba.openbanking;

import com.cba.openbanking.dto.DomesticPaymentRequest;
import com.cba.openbanking.dto.DomesticPaymentResponse;
import com.cba.partner.PartnerWebhookDeliveryService;
import com.cba.payment.PaymentService;
import com.cba.payment.dto.TransferRequest;
import com.cba.payment.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * FAPI 2.0 compliant Payment Initiation Service Provider (PISP) endpoints.
 * Validates an authorised consent before delegating to the core PaymentService.
 */
@RestController
@RequestMapping("/open-banking/v3.1/pisp")
@RequiredArgsConstructor
@Tag(name = "Open Banking — PISP", description = "UK Open Banking v3.1 Payment Initiation")
@SecurityRequirement(name = "oauth2")
public class PispController {

    private final ConsentService consentService;
    private final PaymentService paymentService;
    private final PartnerWebhookDeliveryService webhookDelivery;

    @PostMapping("/domestic-payments")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Initiate a domestic payment (PISP)")
    public ResponseEntity<DomesticPaymentResponse> initiateDomesticPayment(
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @Valid @RequestBody DomesticPaymentRequest request) {

        // Validate that the consent is authorised and has payment scope
        consentService.validatePispConsent(request.consentId());

        TransferRequest transfer = new TransferRequest(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.reference(),
                null
        );
        PaymentResponse payment = paymentService.transfer(transfer, "open-banking:" + request.consentId());

        // Notify the initiating partner (if this consent belongs to one) of the payment outcome
        UUID partnerOrg = PartnerWebhookDeliveryService.parseOrg(consentService.tppClientIdFor(request.consentId()));
        if (partnerOrg != null) {
            webhookDelivery.publishEvent(partnerOrg, "PAYMENT.INITIATED", Map.of(
                    "paymentId", payment.id().toString(),
                    "consentId", request.consentId(),
                    "amount", payment.amount()));
            String terminal = switch (payment.status().name()) {
                case "COMPLETED" -> "PAYMENT.COMPLETED";
                case "FAILED" -> "PAYMENT.FAILED";
                default -> null;
            };
            if (terminal != null) {
                webhookDelivery.publishEvent(partnerOrg, terminal, Map.of(
                        "paymentId", payment.id().toString(),
                        "status", payment.status().name()));
            }
        }

        DomesticPaymentResponse response = new DomesticPaymentResponse(
                payment.id().toString(),
                request.consentId(),
                payment.status().name(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                payment.amount(),
                request.currency(),
                request.reference(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/domestic-payments/{domesticPaymentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Get a domestic payment by ID (PISP)")
    public ResponseEntity<DomesticPaymentResponse> getDomesticPayment(
            @PathVariable String domesticPaymentId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId) {

        PaymentResponse payment = paymentService.getPayment(
                java.util.UUID.fromString(domesticPaymentId));

        DomesticPaymentResponse response = new DomesticPaymentResponse(
                payment.id().toString(),
                null,
                payment.status().name(),
                payment.sourceAccountId(),
                payment.destinationAccountId(),
                payment.amount(),
                payment.currencyCode(),
                payment.description(),
                payment.executedDate()
        );
        return ResponseEntity.ok(response);
    }
}

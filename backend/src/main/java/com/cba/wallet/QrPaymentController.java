package com.cba.wallet;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "QR Payments", description = "Generate and redeem QR payment codes")
public class QrPaymentController {

    private final QrPaymentService qrPaymentService;

    public record GenerateRequest(
            @NotNull UUID accountId,
            BigDecimal presetAmount,
            String reference,
            Integer expiryMinutes
    ) {}

    public record DecodeAndPayRequest(
            @NotBlank String token,
            @NotNull UUID payerAccountId,
            BigDecimal amount
    ) {}

    // ── Generate QR ───────────────────────────────────────────────────────────

    @PostMapping("/api/v1/payments/qr/generate")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Generate a QR code for an account — returns base64 PNG + token")
    public ResponseEntity<ApiResponse<QrPaymentService.QrResponse>> generate(
            @Valid @RequestBody GenerateRequest req) {
        var response = qrPaymentService.generateQr(new QrPaymentService.GenerateQrRequest(
                req.accountId(), req.presetAmount(), req.reference(), req.expiryMinutes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // ── Convenience: generate QR for a specific account ──────────────────────

    @GetMapping("/api/v1/accounts/{accountId}/qr")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Get or refresh a QR code for an account (60-minute default expiry)")
    public ResponseEntity<ApiResponse<QrPaymentService.QrResponse>> getAccountQr(
            @PathVariable UUID accountId,
            @RequestParam(required = false) Integer expiryMinutes) {
        var response = qrPaymentService.refreshQr(accountId, expiryMinutes);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Decode & Pay ──────────────────────────────────────────────────────────

    @PostMapping("/api/v1/payments/qr/decode-and-pay")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    @Operation(summary = "Scan a QR code and execute a payment — token is marked used on success")
    public ResponseEntity<ApiResponse<Object>> decodeAndPay(
            @Valid @RequestBody DecodeAndPayRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        String initiatedBy = jwt != null ? jwt.getSubject() : "system";
        var result = qrPaymentService.decodeAndPay(
                new QrPaymentService.DecodeAndPayRequest(req.token(), req.payerAccountId(), req.amount()),
                initiatedBy);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}

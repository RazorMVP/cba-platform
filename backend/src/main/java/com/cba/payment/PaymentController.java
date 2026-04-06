package com.cba.payment;

import com.cba.common.response.ApiResponse;
import com.cba.payment.dto.PaymentResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Internal transfers and payment operations")
@SecurityRequirement(name = "oauth2")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Execute an internal transfer between two accounts")
    public ResponseEntity<ApiResponse<PaymentResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(paymentService.transfer(request, actor)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get payment details by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getPayment(id)));
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get payment history for an account")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getAccountPayments(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PaymentResponse> page = paymentService.getAccountPayments(accountId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
            ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }
}

package com.cba.selfservice;

import com.cba.account.dto.AccountResponse;
import com.cba.account.dto.TransactionResponse;
import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import com.cba.customer.Beneficiary;
import com.cba.customer.BeneficiaryService;
import com.cba.customer.dto.CustomerResponse;
import com.cba.loan.dto.LoanResponse;
import com.cba.loan.dto.RepaymentScheduleResponse;
import com.cba.wallet.PocketService;
import com.cba.wallet.QrPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Self-service endpoints — customers access only their own data.
 *
 * The JWT sub claim is the Keycloak user ID. We resolve the customer record
 * by matching keycloak_id in the customers table (or platform_users → customers).
 * Every endpoint enforces that the requested resource belongs to the caller.
 */
@RestController
@RequestMapping("/api/v1/self")
@RequiredArgsConstructor
@Tag(name = "Self Service", description = "Customer self-service — authenticated customers access only their own data")
public class SelfServiceController {

    private final SelfServiceFacade selfServiceFacade;

    @GetMapping("/userdetails")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get own profile details")
    public ResponseEntity<ApiResponse<CustomerResponse>> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(selfServiceFacade.getProfile(sub(jwt))));
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List own accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(selfServiceFacade.getAccounts(sub(jwt))));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List transactions on own account")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getMyTransactions(
            @PathVariable UUID accountId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(selfServiceFacade.getTransactions(sub(jwt), accountId)));
    }

    @GetMapping("/loans")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List own loans")
    public ResponseEntity<ApiResponse<List<LoanResponse>>> getMyLoans(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(selfServiceFacade.getLoans(sub(jwt))));
    }

    @GetMapping("/loans/{loanId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get details of own loan including repayment schedule")
    public ResponseEntity<ApiResponse<LoanResponse>> getMyLoan(
            @PathVariable UUID loanId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(selfServiceFacade.getLoan(sub(jwt), loanId)));
    }

    @GetMapping("/loans/{loanId}/repayment-schedule")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "View repayment schedule for own loan")
    public ResponseEntity<ApiResponse<List<RepaymentScheduleResponse>>> getMyRepaymentSchedule(
            @PathVariable UUID loanId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(selfServiceFacade.getRepaymentSchedule(sub(jwt), loanId)));
    }

    @PostMapping("/loanapplications")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Apply for a loan as authenticated customer")
    public ResponseEntity<ApiResponse<LoanResponse>> applyForLoan(
            @Valid @RequestBody SelfServiceFacade.SelfLoanApplicationRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(selfServiceFacade.applyForLoan(sub(jwt), req)));
    }

    // ── Self-Service Beneficiaries ────────────────────────────────────────────

    @GetMapping("/beneficiaries")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List own beneficiaries")
    public ResponseEntity<ApiResponse<List<Beneficiary>>> getMyBeneficiaries(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(selfServiceFacade.getBeneficiaries(sub(jwt))));
    }

    @PostMapping("/beneficiaries")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Add a beneficiary")
    public ResponseEntity<ApiResponse<Beneficiary>> addBeneficiary(
            @Valid @RequestBody BeneficiaryService.CreateBeneficiaryRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(selfServiceFacade.addBeneficiary(sub(jwt), req)));
    }

    @DeleteMapping("/beneficiaries/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Remove a beneficiary (soft delete)")
    public ResponseEntity<ApiResponse<Void>> removeBeneficiary(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        selfServiceFacade.removeBeneficiary(sub(jwt), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Self-Service Pockets ──────────────────────────────────────────────────

    public record SelfCreatePocketRequest(String name, String description, List<UUID> accountIds) {}
    public record SelfLinkRequest(@NotNull List<UUID> accountIds) {}

    @GetMapping("/pockets")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List own pockets")
    public ResponseEntity<ApiResponse<List<PocketService.PocketResponse>>> getMyPockets(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(selfServiceFacade.getMyPockets(sub(jwt))));
    }

    @PostMapping("/pockets")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create a pocket")
    public ResponseEntity<ApiResponse<PocketService.PocketResponse>> createPocket(
            @Valid @RequestBody SelfCreatePocketRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        var created = selfServiceFacade.createPocket(sub(jwt),
                new PocketService.CreatePocketRequest(null, req.name(), req.description(), req.accountIds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PostMapping("/pockets/{id}/link")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Link savings accounts to a pocket")
    public ResponseEntity<ApiResponse<PocketService.PocketResponse>> linkAccounts(
            @PathVariable UUID id,
            @Valid @RequestBody SelfLinkRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(
                selfServiceFacade.linkAccountsToPocket(sub(jwt), id, req.accountIds())));
    }

    @PostMapping("/pockets/{id}/delink")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Remove savings accounts from a pocket")
    public ResponseEntity<ApiResponse<PocketService.PocketResponse>> delinkAccounts(
            @PathVariable UUID id,
            @Valid @RequestBody SelfLinkRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(
                selfServiceFacade.delinkAccountsFromPocket(sub(jwt), id, req.accountIds())));
    }

    // ── Self-Service QR Payments ──────────────────────────────────────────────

    @GetMapping("/accounts/{accountId}/qr")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Generate a QR code for own account — returns base64 PNG")
    public ResponseEntity<ApiResponse<QrPaymentService.QrResponse>> generateQr(
            @PathVariable UUID accountId,
            @RequestParam(required = false) BigDecimal presetAmount,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Integer expiryMinutes,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(
                selfServiceFacade.generateQrForAccount(sub(jwt), accountId, presetAmount, reference, expiryMinutes)));
    }

    public record SelfScanAndPayRequest(@NotNull String token, @NotNull UUID payerAccountId, BigDecimal amount) {}

    @PostMapping("/payments/scan-and-pay")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Scan a QR code and pay from own account")
    public ResponseEntity<ApiResponse<Object>> scanAndPay(
            @Valid @RequestBody SelfScanAndPayRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(
                selfServiceFacade.scanAndPay(sub(jwt), req.token(), req.payerAccountId(), req.amount())));
    }

    private String sub(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw CbaException.badRequest("MISSING_JWT_SUB", "JWT subject claim is missing");
        }
        return sub;
    }
}

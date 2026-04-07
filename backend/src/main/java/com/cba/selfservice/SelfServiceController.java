package com.cba.selfservice;

import com.cba.account.dto.AccountResponse;
import com.cba.account.dto.TransactionResponse;
import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import com.cba.customer.dto.CustomerResponse;
import com.cba.loan.dto.LoanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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

    private String sub(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw CbaException.badRequest("MISSING_JWT_SUB", "JWT subject claim is missing");
        }
        return sub;
    }
}

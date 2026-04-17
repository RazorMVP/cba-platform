package com.cba.account;

import com.cba.account.dto.AccountResponse;
import com.cba.account.dto.OpenAccountRequest;
import com.cba.account.dto.TransactionResponse;
import com.cba.common.response.ApiResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management and transaction operations")
@SecurityRequirement(name = "oauth2")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Open a new account for a customer")
    public ResponseEntity<ApiResponse<AccountResponse>> openAccount(
            @Valid @RequestBody OpenAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(accountService.openAccount(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get account details")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getAccount(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List accounts for a customer")
    public ResponseEntity<ApiResponse<Page<AccountResponse>>> getCustomerAccounts(
            @RequestParam UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AccountResponse> page = accountService.getCustomerAccounts(customerId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
            ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Execute account lifecycle command (approve, activate, reject)")
    public ResponseEntity<ApiResponse<AccountResponse>> executeCommand(
            @PathVariable UUID id,
            @RequestParam String command) {
        AccountResponse response = switch (command.toLowerCase()) {
            case "approve"  -> accountService.approveAccount(id);
            case "activate" -> accountService.activateAccount(id);
            case "reject"   -> accountService.rejectAccount(id);
            default         -> throw com.cba.common.exception.CbaException.badRequest(
                                   "UNKNOWN_COMMAND", "Unknown command: " + command);
        };
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Change account status (ACTIVE, FROZEN, DORMANT, CLOSED)")
    public ResponseEntity<ApiResponse<AccountResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestParam AccountStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.updateStatus(id, status)));
    }

    @PostMapping("/{id}/deposit")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Deposit funds into an account (teller operation)")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(ApiResponse.ok(
            accountService.deposit(id, amount, description, actor)));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Withdraw funds from an account (teller operation)")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(ApiResponse.ok(
            accountService.withdraw(id, amount, description, actor)));
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get paginated transaction history for an account")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "transactionDate") Pageable pageable) {
        Page<TransactionResponse> page = accountService.getTransactions(id, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
            ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    @GetMapping("/{id}/statement")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Generate an account statement for a date range")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatement(
            @PathVariable UUID id,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @PageableDefault(size = 500) Pageable pageable) {
        AccountResponse account = accountService.getAccount(id);
        Page<TransactionResponse> txns = accountService.getTransactionsByDateRange(id, from, to, pageable);
        BigDecimal totalDebits = txns.getContent().stream()
                .filter(t -> t.amount().compareTo(BigDecimal.ZERO) < 0)
                .map(t -> t.amount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = txns.getContent().stream()
                .filter(t -> t.amount().compareTo(BigDecimal.ZERO) > 0)
                .map(TransactionResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> statement = Map.of(
                "accountNumber", account.accountNumber(),
                "accountType",   account.accountType(),
                "currency",      account.currencyCode(),
                "statementFrom", from,
                "statementTo",   to,
                "closingBalance", account.balance(),
                "totalCredits",  totalCredits,
                "totalDebits",   totalDebits,
                "transactionCount", txns.getTotalElements(),
                "transactions",  txns.getContent()
        );
        return ResponseEntity.ok(ApiResponse.ok(statement));
    }

    @GetMapping("/{id}/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    @Operation(summary = "Get the product configuration (interest rates, limits) applied to this account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccountTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getAccountTemplate(id)));
    }
}

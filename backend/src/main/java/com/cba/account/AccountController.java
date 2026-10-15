package com.cba.account;

import com.cba.account.dto.AccountHoldRequest;
import com.cba.account.dto.AccountHoldResponse;
import com.cba.account.dto.AccountResponse;
import com.cba.account.dto.CardFunds;
import com.cba.account.dto.CardHoldRequest;
import com.cba.account.dto.CardHoldResult;
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
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management and transaction operations")
@SecurityRequirement(name = "oauth2")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get available deposit products and account types for opening a new account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOpenAccountTemplate() {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getOpenAccountTemplate()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Open a new account for a customer")
    public ResponseEntity<ApiResponse<AccountResponse>> openAccount(
            @Valid @RequestBody OpenAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(accountService.openAccount(request)));
    }

    /**
     * What a card authorization may spend — card-service's debit-card balance check, with its
     * service token (Keycloak client cba-card-service, realm role CARD_SERVICE). No PII.
     */
    @GetMapping("/{id}/balance")
    @PreAuthorize("hasRole('CARD_SERVICE') or hasAnyRole('ADMIN','TELLER')")
    @Operation(summary = "Spendable balance for a card authorization (card-service)",
               description = "availableBalance = balance − active holds − minimum balance (or + overdraft limit), "
                       + "major units of currencyCode; 0 with debitAllowed=false and a reason "
                       + "(ACCOUNT_NOT_ACTIVE | ACCOUNT_IN_LOCKIN_PERIOD) when the account can't be debited.")
    public ResponseEntity<ApiResponse<CardFunds>> getCardFunds(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.cardFunds(id)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER') or (hasRole('CUSTOMER') and @customerAccess.isAccountOwner(#id))")
    @Operation(summary = "Get account details")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getAccount(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List accounts for a customer, or retrieve the open-account template when ?template=true")
    public ResponseEntity<?> getCustomerAccounts(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false, defaultValue = "false") boolean template,
            @PageableDefault(size = 20) Pageable pageable) {
        if (template) {
            return ResponseEntity.ok(ApiResponse.ok(accountService.getOpenAccountTemplate()));
        }
        if (customerId == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("MISSING_PARAM", "customerId is required when template=false"));
        }
        Page<AccountResponse> page = accountService.getCustomerAccounts(customerId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
            ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Execute account lifecycle command (approve, activate, reject, reactivate)")
    public ResponseEntity<ApiResponse<AccountResponse>> executeCommand(
            @PathVariable UUID id,
            @RequestParam String command) {
        AccountResponse response = switch (command.toLowerCase(Locale.ROOT)) {
            case "approve"      -> accountService.approveAccount(id);
            case "activate"     -> accountService.activateAccount(id);
            case "reject"       -> accountService.rejectAccount(id);
            case "reactivate"   -> accountService.reactivateAccount(id);
            case "postinterest" -> accountService.postInterest(id);
            default             -> throw com.cba.common.exception.CbaException.badRequest(
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
    @PreAuthorize("hasAnyRole('ADMIN','TELLER') or (hasRole('CUSTOMER') and @customerAccess.isAccountOwner(#id))")
    @Operation(summary = "Get paginated transaction history for an account; optionally filter by transactionType")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            @PathVariable UUID id,
            @RequestParam(required = false) TransactionType transactionType,
            @PageableDefault(size = 20, sort = "transactionDate") Pageable pageable) {
        Page<TransactionResponse> page = transactionType != null
            ? accountService.getTransactionsByType(id, transactionType, pageable)
            : accountService.getTransactions(id, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page,
            ApiResponse.PageMeta.of(page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    @GetMapping("/{id}/statement")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER') or (hasRole('CUSTOMER') and @customerAccess.isAccountOwner(#id))")
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
    @PreAuthorize("hasAnyRole('ADMIN','TELLER') or (hasRole('CUSTOMER') and @customerAccess.isAccountOwner(#id))")
    @Operation(summary = "Get the product configuration (interest rates, limits) applied to this account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccountTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getAccountTemplate(id)));
    }

    // ── Hold endpoints ────────────────────────────────────────────────────────

    @GetMapping("/{id}/holds")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all holds on an account")
    public ResponseEntity<ApiResponse<java.util.List<AccountHoldResponse>>> getHolds(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getHolds(id)));
    }

    @PostMapping("/{id}/holds")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Place a funds hold on an account")
    public ResponseEntity<ApiResponse<AccountHoldResponse>> placeHold(
            @PathVariable UUID id,
            @Valid @RequestBody AccountHoldRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt != null ? jwt.getClaimAsString("preferred_username") : "system";
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(accountService.placeHold(id, request, actor)));
    }

    @GetMapping("/{id}/interest/calculate")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Preview the daily interest that would be posted to this account (dry-run)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateInterest(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.calculateInterest(id)));
    }

    /**
     * Reserves a card authorization's amount — card-service, on every debit-card approval. Locks the
     * account, applies the withdrawal rules and places a hold atomically; idempotent on
     * {@code reference}. A decline is 200 with approved=false and a reason.
     */
    @PostMapping("/{id}/card-holds")
    @PreAuthorize("hasRole('CARD_SERVICE') or hasRole('ADMIN')")
    @Operation(summary = "Reserve funds for a card authorization (card-service)",
               description = "Atomic check-and-hold under the account's row lock. reasons: INSUFFICIENT_FUNDS, "
                       + "ACCOUNT_NOT_ACTIVE, ACCOUNT_IN_LOCKIN_PERIOD, CURRENCY_MISMATCH, HOLD_RELEASED (reference "
                       + "already used and released). The hold expires after card.hold-expiry-days (7).")
    public ResponseEntity<ApiResponse<CardHoldResult>> placeCardHold(
            @PathVariable UUID id,
            @Valid @RequestBody CardHoldRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.placeCardHold(id, request, serviceActor(jwt))));
    }

    /** Releases a card authorization's hold (reversal). Idempotent. */
    @PostMapping("/{id}/card-holds/{reference}/release")
    @PreAuthorize("hasRole('CARD_SERVICE') or hasRole('ADMIN')")
    @Operation(summary = "Release a card authorization's hold (card-service reversal)",
               description = "Idempotent: an already released, expired or unknown hold is reported with approved=false "
                       + "(HOLD_NOT_ACTIVE / HOLD_NOT_FOUND).")
    public ResponseEntity<ApiResponse<CardHoldResult>> releaseCardHold(
            @PathVariable UUID id,
            @PathVariable String reference,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.releaseCardHold(id, reference, serviceActor(jwt))));
    }

    /** A service account has no preferred_username of a person; fall back to the client id. */
    private static String serviceActor(Jwt jwt) {
        if (jwt == null) return "system";
        String user = jwt.getClaimAsString("preferred_username");
        return user != null ? user : jwt.getClaimAsString("azp");
    }

    @DeleteMapping("/{id}/holds/{holdId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Release a specific hold on an account")
    public ResponseEntity<ApiResponse<AccountHoldResponse>> releaseHold(
            @PathVariable UUID id,
            @PathVariable UUID holdId,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = jwt != null ? jwt.getClaimAsString("preferred_username") : "system";
        return ResponseEntity.ok(ApiResponse.ok(accountService.releaseHold(id, holdId, actor)));
    }
}

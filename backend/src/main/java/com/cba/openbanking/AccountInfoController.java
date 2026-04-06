package com.cba.openbanking;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.account.TransactionRepository;
import com.cba.common.exception.CbaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FAPI 2.0 compliant Account Information Service Provider (AISP) endpoints.
 * Base path: /open-banking/v3.1/aisp
 * Requires: x-fapi-interaction-id header (correlation ID for audit trail)
 */
@RestController
@RequestMapping("/open-banking/v3.1/aisp")
@RequiredArgsConstructor
@Tag(name = "Open Banking — AISP", description = "UK Open Banking v3.1 Account Information")
@SecurityRequirement(name = "oauth2")
public class AccountInfoController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "List accounts for the authenticated customer (AISP)")
    public ResponseEntity<Map<String, Object>> getAccounts(
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID customerId = resolveCustomerId(jwt);
        List<Account> accounts = accountRepository.findByCustomerId(customerId, PageRequest.of(0, 100)).getContent();

        List<Map<String, Object>> accountData = accounts.stream().map(a -> Map.<String, Object>of(
            "AccountId",  a.getId().toString(),
            "AccountNumber", maskAccountNumber(a.getAccountNumber()),
            "AccountType", a.getAccountType().name(),
            "Status",     a.getStatus().name(),
            "Currency",   a.getCurrencyCode()
        )).toList();

        return ResponseEntity.ok(Map.of("Data", Map.of("Account", accountData),
            "Meta", Map.of("TotalPages", 1),
            "Links", Map.of("Self", "/open-banking/v3.1/aisp/accounts")));
    }

    @GetMapping("/accounts/{accountId}/balances")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Get balance for a specific account (AISP)")
    public ResponseEntity<Map<String, Object>> getBalances(
            @PathVariable UUID accountId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID customerId = resolveCustomerId(jwt);
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> CbaException.notFound("Account", accountId));

        if (!account.getCustomer().getId().equals(customerId)) {
            throw CbaException.forbidden("Access denied to account " + accountId);
        }

        Map<String, Object> balance = Map.of(
            "AccountId",    accountId.toString(),
            "Amount",       Map.of("Amount", account.getBalance().toPlainString(),
                                   "Currency", account.getCurrencyCode()),
            "CreditDebitIndicator", "Credit",
            "Type",         "ClosingAvailable",
            "DateTime",     Instant.now().toString()
        );

        return ResponseEntity.ok(Map.of(
            "Data",  Map.of("Balance", List.of(balance)),
            "Meta",  Map.of("TotalPages", 1),
            "Links", Map.of("Self", "/open-banking/v3.1/aisp/accounts/" + accountId + "/balances")
        ));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Get transactions for a specific account (AISP)")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime fromBookingDateTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime toBookingDateTime,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID customerId = resolveCustomerId(jwt);
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> CbaException.notFound("Account", accountId));

        if (!account.getCustomer().getId().equals(customerId)) {
            throw CbaException.forbidden("Access denied to account " + accountId);
        }

        var transactions = transactionRepository.findByAccountId(accountId, PageRequest.of(0, 100));
        List<Map<String, Object>> txData = transactions.getContent().stream().map(t -> Map.<String, Object>of(
            "TransactionId",       t.getId().toString(),
            "AccountId",           accountId.toString(),
            "CreditDebitIndicator", isCredit(t.getTransactionType()) ? "Credit" : "Debit",
            "Status",              "Booked",
            "BookingDateTime",     t.getTransactionDate().toString(),
            "Amount",              Map.of("Amount", t.getAmount().toPlainString(),
                                          "Currency", t.getCurrencyCode()),
            "TransactionInformation", t.getDescription() != null ? t.getDescription() : ""
        )).toList();

        return ResponseEntity.ok(Map.of(
            "Data",  Map.of("Transaction", txData),
            "Meta",  Map.of("TotalPages", 1),
            "Links", Map.of("Self", "/open-banking/v3.1/aisp/accounts/" + accountId + "/transactions")
        ));
    }

    private UUID resolveCustomerId(Jwt jwt) {
        // In production: resolve customer UUID from JWT sub claim via a user-mapping service
        // For now: look up by preferred_username (sufficient for demo/dev)
        String sub = jwt.getSubject();
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw CbaException.badRequest("INVALID_SUBJECT", "Cannot resolve customer from token subject");
        }
    }

    private String maskAccountNumber(String number) {
        if (number == null || number.length() < 4) return "****";
        return "****" + number.substring(number.length() - 4);
    }

    private boolean isCredit(com.cba.account.TransactionType type) {
        return switch (type) {
            case DEPOSIT, TRANSFER_CREDIT, LOAN_DISBURSEMENT, INTEREST_CREDIT -> true;
            default -> false;
        };
    }
}

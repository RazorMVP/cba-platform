package com.cba.openbanking;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.account.TransactionRepository;
import com.cba.common.exception.CbaException;
import com.cba.config.CustomerAccess;
import com.cba.openbanking.card.CardAccountAdapter;
import com.cba.openbanking.card.CardServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FAPI 2.0 compliant Account Information Service Provider (AISP) endpoints.
 * Base path: /open-banking/v3.1/aisp
 *
 * <p>Card accounts (from card-service) are merged into bank accounts. If card-service
 * is unavailable, responses degrade gracefully — bank accounts are always returned.
 *
 * <p>A CUSTOMER reads their own data. A TPP ({@code API_CLIENT}, partner API key) names its consent
 * in {@code x-consent-id}; the customer is the consent's, and each resource needs the consent's scope
 * for it ({@code accounts_read} / {@code balances_read} / {@code transactions_read} for bank accounts,
 * {@code card_read} / {@code card_balances_read} / {@code card_transactions_read} for cards).
 */
@RestController
@RequestMapping("/open-banking/v3.1/aisp")
@RequiredArgsConstructor
@Tag(name = "Open Banking — AISP", description = "UK Open Banking v3.1 Account Information")
@SecurityRequirement(name = "oauth2")
public class AccountInfoController {

    private final AccountRepository     accountRepository;
    private final TransactionRepository transactionRepository;
    private final CardServiceClient     cardServiceClient;
    private final CustomerAccess        customerAccess;
    private final ConsentService        consentService;

    static final String CONSENT_HEADER = "x-consent-id";

    // ── GET /accounts ─────────────────────────────────────────────────────────

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "List accounts and cards for the authenticated customer (AISP)")
    public ResponseEntity<Map<String, Object>> getAccounts(
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @RequestHeader(value = CONSENT_HEADER, required = false) String consentId) {

        Reader reader = reader(consentId, ConsentScope.ACCOUNTS_READ, ConsentScope.CARD_READ);
        UUID customerId = reader.customerId();

        // 1. Bank accounts (monolith)
        List<Account> accounts = reader.may(ConsentScope.ACCOUNTS_READ)
                ? accountRepository.findByCustomerId(customerId, PageRequest.of(0, 100)).getContent()
                : List.of();
        List<Map<String, Object>> accountData = new ArrayList<>(accounts.stream()
                .map(a -> Map.<String, Object>of(
                        "AccountId",   a.getId().toString(),
                        "AccountNumber", maskAccountNumber(a.getAccountNumber()),
                        "AccountType", a.getAccountType().name(),
                        "AccountSubType", "CurrentAccount",
                        "Status",      a.getStatus().name(),
                        "Currency",    a.getCurrencyCode()
                )).toList());

        // 2. Cards (card-service) — merged in; silently omitted if card-service is down
        List<CardServiceClient.CardDto> cards = reader.may(ConsentScope.CARD_READ)
                ? cardServiceClient.getCardsForCustomer(customerId)
                : List.of();
        cards.stream()
                .filter(c -> "ACTIVE".equals(c.status()) || "ISSUED".equals(c.status()))
                .map(CardAccountAdapter::toObAccount)
                .forEach(accountData::add);

        return ResponseEntity.ok(Map.of(
                "Data",  Map.of("Account", accountData),
                "Meta",  Map.of("TotalPages", 1, "TotalCount", accountData.size()),
                "Links", Map.of("Self", "/open-banking/v3.1/aisp/accounts")));
    }

    // ── GET /accounts/{accountId}/balances ────────────────────────────────────

    @GetMapping("/accounts/{accountId}/balances")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Get balance for an account or card (AISP)")
    public ResponseEntity<Map<String, Object>> getBalances(
            @PathVariable UUID accountId,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @RequestHeader(value = CONSENT_HEADER, required = false) String consentId) {

        Reader reader = reader(consentId, ConsentScope.BALANCES_READ, ConsentScope.CARD_BALANCES_READ);
        UUID customerId = reader.customerId();

        // Try bank account first
        var bankAccount = accountRepository.findById(accountId);
        if (bankAccount.isPresent()) {
            Account account = bankAccount.get();
            enforceOwnership(account.getCustomer().getId(), customerId, accountId);
            reader.require(ConsentScope.BALANCES_READ);
            Map<String, Object> balance = Map.of(
                    "AccountId",            accountId.toString(),
                    "Amount",               Map.of("Amount", account.getBalance().toPlainString(),
                                                   "Currency", account.getCurrencyCode()),
                    "CreditDebitIndicator", "Credit",
                    "Type",                 "ClosingAvailable",
                    "DateTime",             Instant.now().toString()
            );
            return ResponseEntity.ok(wrapBalance(accountId, balance));
        }

        // Fall back to card account
        var cardOpt = cardServiceClient.getCard(accountId);
        if (cardOpt.isPresent()) {
            CardServiceClient.CardDto card = cardOpt.get();
            enforceCardOwnership(card.customerId(), customerId, accountId);
            reader.require(ConsentScope.CARD_BALANCES_READ);
            var balOpt = cardServiceClient.getCardBalance(accountId);
            String ccy = deriveCurrency(card);
            Map<String, Object> balance = balOpt.isPresent()
                    ? CardAccountAdapter.toObBalance(accountId.toString(), balOpt.get(), ccy)
                    : Map.of("AccountId", accountId.toString(),
                             "Amount", Map.of("Amount", "0.00", "Currency", ccy),
                             "CreditDebitIndicator", "Credit",
                             "Type", "ClosingAvailable",
                             "DateTime", Instant.now().toString());
            return ResponseEntity.ok(wrapBalance(accountId, balance));
        }

        throw CbaException.notFound("Account", accountId);
    }

    // ── GET /accounts/{accountId}/transactions ────────────────────────────────

    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'API_CLIENT')")
    @Operation(summary = "Get transactions or authorizations for an account or card (AISP)")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime fromBookingDateTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime toBookingDateTime,
            @RequestHeader(value = "x-fapi-interaction-id", required = false) String interactionId,
            @RequestHeader(value = CONSENT_HEADER, required = false) String consentId) {

        Reader reader = reader(consentId, ConsentScope.TRANSACTIONS_READ, ConsentScope.CARD_TRANSACTIONS_READ);
        UUID customerId = reader.customerId();

        // Try bank account first
        var bankAccount = accountRepository.findById(accountId);
        if (bankAccount.isPresent()) {
            Account account = bankAccount.get();
            enforceOwnership(account.getCustomer().getId(), customerId, accountId);
            reader.require(ConsentScope.TRANSACTIONS_READ);
            var transactions = transactionRepository.findByAccountId(accountId, PageRequest.of(0, 100));
            List<Map<String, Object>> txData = transactions.getContent().stream()
                    .map(t -> Map.<String, Object>of(
                            "TransactionId",          t.getId().toString(),
                            "AccountId",              accountId.toString(),
                            "CreditDebitIndicator",   isCredit(t.getTransactionType()) ? "Credit" : "Debit",
                            "Status",                 "Booked",
                            "BookingDateTime",        t.getTransactionDate().toString(),
                            "Amount",                 Map.of("Amount", t.getAmount().toPlainString(),
                                                             "Currency", t.getCurrencyCode()),
                            "TransactionInformation", t.getDescription() != null ? t.getDescription() : ""
                    )).toList();
            return ResponseEntity.ok(wrapTransactions(accountId, txData));
        }

        // Fall back to card authorization history
        var cardOpt = cardServiceClient.getCard(accountId);
        if (cardOpt.isPresent()) {
            enforceCardOwnership(cardOpt.get().customerId(), customerId, accountId);
            reader.require(ConsentScope.CARD_TRANSACTIONS_READ);
            List<Map<String, Object>> txData = cardServiceClient
                    .getCardAuthorizations(accountId).stream()
                    .map(auth -> CardAccountAdapter.toObTransaction(accountId.toString(), auth))
                    .toList();
            return ResponseEntity.ok(wrapTransactions(accountId, txData));
        }

        throw CbaException.notFound("Account", accountId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Whose data the call reads, and under which consent. A customer's token carries their Keycloak
     * id as {@code sub}, which maps to {@code customers.keycloak_id}; no consent is involved. A TPP
     * reads only through its own usable consent (see {@link ConsentService#requireUsableConsent}),
     * which must grant at least one of {@code anyOf}; the customer is the consent's.
     */
    private Reader reader(String consentId, ConsentScope... anyOf) {
        boolean customer = SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        if (customer) {
            UUID customerId = customerAccess.callerCustomerId()
                    .orElseThrow(() -> CbaException.notFound("Customer", CustomerAccess.callerSubject()));
            return new Reader(customerId, null);
        }
        if (consentId == null || consentId.isBlank()) {
            throw CbaException.badRequest("CONSENT_ID_REQUIRED",
                    "TPP calls must name their consent in the " + CONSENT_HEADER + " header");
        }
        UsableConsent consent = consentService.requireUsableConsent(consentId, anyOf);
        return new Reader(consent.customerId(), consent);
    }

    /** The customer being read; {@code consent} is null for the customer themselves (every scope). */
    private record Reader(UUID customerId, UsableConsent consent) {
        boolean may(ConsentScope scope) {
            return consent == null || consent.has(scope);
        }

        void require(ConsentScope scope) {
            if (consent != null) consent.require(scope);
        }
    }

    private void enforceOwnership(UUID ownerId, UUID requestingCustomerId, UUID resourceId) {
        if (!ownerId.equals(requestingCustomerId)) {
            // Return 404 not 403 — prevents resource enumeration
            throw CbaException.notFound("Account", resourceId);
        }
    }

    private void enforceCardOwnership(UUID cardCustomerId, UUID requestingCustomerId, UUID resourceId) {
        if (cardCustomerId == null || !cardCustomerId.equals(requestingCustomerId)) {
            throw CbaException.notFound("Account", resourceId);
        }
    }

    private String maskAccountNumber(String number) {
        if (number == null || number.length() < 4) return "****";
        return "****" + number.substring(number.length() - 4);
    }

    private String deriveCurrency(CardServiceClient.CardDto card) {
        // Prefer card's own currency if available — fall back to USD (ISO numeric "840")
        return "840";
    }

    private boolean isCredit(com.cba.account.TransactionType type) {
        return switch (type) {
            case DEPOSIT, TRANSFER_CREDIT, LOAN_DISBURSEMENT, INTEREST_CREDIT -> true;
            default -> false;
        };
    }

    private Map<String, Object> wrapBalance(UUID accountId, Map<String, Object> balance) {
        return Map.of(
                "Data",  Map.of("Balance", List.of(balance)),
                "Meta",  Map.of("TotalPages", 1),
                "Links", Map.of("Self", "/open-banking/v3.1/aisp/accounts/" + accountId + "/balances"));
    }

    private Map<String, Object> wrapTransactions(UUID accountId, List<Map<String, Object>> txData) {
        return Map.of(
                "Data",  Map.of("Transaction", txData),
                "Meta",  Map.of("TotalPages", 1),
                "Links", Map.of("Self", "/open-banking/v3.1/aisp/accounts/" + accountId + "/transactions"));
    }
}

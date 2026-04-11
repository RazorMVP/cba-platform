package com.cba.card.openbanking;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.CardAuthorizationService;
import com.cba.card.card.Card;
import com.cba.card.card.CardService;
import com.cba.card.common.ApiResponse;
import com.cba.card.common.CbaException;
import com.cba.card.limits.CardLimit;
import com.cba.card.limits.CardLimitService;
import com.cba.card.openbanking.analytics.SpendingAnalyticsService;
import com.cba.card.openbanking.apikey.ApiKey;
import com.cba.card.openbanking.apikey.ApiKeyAuthentication;
import com.cba.card.openbanking.apikey.ApiKeyService;
import com.cba.card.openbanking.webhook.Webhook;
import com.cba.card.openbanking.webhook.WebhookDeliveryLog;
import com.cba.card.openbanking.webhook.WebhookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Card Open Banking API — all {@code /card-api/v1/} endpoints.
 *
 * <h3>Authentication modes</h3>
 * <ul>
 *   <li><b>API Key</b> ({@code Authorization: ApiKey cba_...}) — M2M / BaaS integrators.
 *       Checked first by {@link com.cba.card.openbanking.apikey.ApiKeyAuthFilter};
 *       principal is the {@link com.cba.card.openbanking.apikey.ApiKeyAuthentication}.</li>
 *   <li><b>FAPI 2.0 JWT</b> ({@code Authorization: Bearer ...}) — customer-facing consent flows.
 *       Standard Keycloak JWT; principal contains Keycloak sub (customer UUID).</li>
 * </ul>
 *
 * <h3>Role matrix</h3>
 * <pre>
 *   ROLE_API_KEY  — card issuance, analytics, webhooks, history
 *   ROLE_ADMIN    — API key management
 *   ROLE_ADMIN / ROLE_TELLER / ROLE_CUSTOMER — card controls (with consent)
 * </pre>
 */
@RestController
@RequestMapping("/card-api/v1")
@RequiredArgsConstructor
public class CardApiController {

    private final ApiKeyService              apiKeyService;
    private final CardService                cardService;
    private final CardLimitService           limitService;
    private final CardAuthorizationService   authorizationService;
    private final WebhookService             webhookService;
    private final SpendingAnalyticsService   analyticsService;

    // ══════════════════════════════════════════════════════════════════════════
    // API Key Management (ADMIN only)
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<IssueKeyResponse>> issueApiKey(
            @Valid @RequestBody IssueKeyRequest req,
            Authentication auth) {

        UUID createdBy = resolveUserId(auth);
        ApiKeyService.IssueResult result = apiKeyService.issueKey(req.name(), createdBy, req.scopes());
        // Return the raw key once — never retrievable again
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new IssueKeyResponse(result.apiKey(), result.rawKey())));
    }

    @GetMapping("/api-keys")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ApiKey>>> listApiKeys() {
        return ResponseEntity.ok(ApiResponse.ok(apiKeyService.listActive()));
    }

    @DeleteMapping("/api-keys/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@PathVariable UUID id) {
        apiKeyService.revoke(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Card Issuance (API Key — BaaS)
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/cards")
    @PreAuthorize("hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<ApiResponse<Card>> issueCard(
            @Valid @RequestBody IssueCardRequest req) {

        Card card = cardService.issueCard(
                req.productId(), req.customerId(), req.linkedEntityId(),
                req.virtual() != null && req.virtual());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(card));
    }

    @GetMapping("/cards")
    @PreAuthorize("hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<ApiResponse<List<Card>>> listCards(
            @RequestParam(required = false) UUID customerId) {

        List<Card> cards = (customerId != null)
                ? cardService.findByCustomer(customerId)
                : cardService.findAll();
        return ResponseEntity.ok(ApiResponse.ok(cards));
    }

    @GetMapping("/cards/{id}")
    @PreAuthorize("hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<Card>> getCard(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(cardService.findById(id)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Card Controls (FAPI 2.0 Consent — customer-facing)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Update card behavioral controls: freeze, contactless, CNP, international.
     * Delegates to {@link CardService#executeCommand} for state transitions.
     */
    @PutMapping("/cards/{id}/controls")
    @PreAuthorize("hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<Card>> updateControls(
            @PathVariable UUID id,
            @Valid @RequestBody CardControlsRequest req) {

        // Map control actions to the card command state machine
        Card card = cardService.findById(id);
        if (Boolean.TRUE.equals(req.freeze())) {
            card = cardService.executeCommand(id, "block");
        } else if (Boolean.FALSE.equals(req.freeze())) {
            card = cardService.executeCommand(id, "unblock");
        }
        // contactless / CNP / international flags are stored in card_limits / product features
        // For now these are advisory flags returned as-is; full implementation wires to card product config
        return ResponseEntity.ok(ApiResponse.ok(card));
    }

    /** Update daily/per-transaction/monthly spending limits for a card. */
    @PutMapping("/cards/{id}/limits")
    @PreAuthorize("hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<CardLimit>> updateLimits(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLimitsRequest req) {

        CardLimit limit = limitService.update(id,
                req.dailyPurchaseLimit(), req.dailyWithdrawalLimit(),
                req.perTransactionLimit(), req.monthlyLimit());
        return ResponseEntity.ok(ApiResponse.ok(limit));
    }

    /**
     * PIN change — routed through HSM adapter.
     * For security, PIN values are never logged; only success/failure is returned.
     */
    @PostMapping("/cards/{id}/pin/change")
    @PreAuthorize("hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePin(
            @PathVariable UUID id,
            @Valid @RequestBody PinChangeRequest req) {

        // HSM-routed PIN change is handled in CardAuthorizationService; proxy here
        boolean changed = authorizationService.changePin(id, req.oldPinBlock(), req.newPinBlock());
        if (!changed) throw CbaException.badRequest("PIN_CHANGE_FAILED", "PIN verification failed");
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "PIN_CHANGED")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Authorization & Transaction History
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/cards/{id}/authorizations")
    @PreAuthorize("hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<List<AuthorizationLog>>> getAuthorizations(
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.ok(authorizationService.getHistory(id)));
    }

    /** Settled transactions — authorized and cleared through a settlement batch. */
    @GetMapping("/cards/{id}/transactions")
    @PreAuthorize("hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<List<AuthorizationLog>>> getSettledTransactions(
            @PathVariable UUID id) {

        // Settled = authorized + response_code 00; filtered from auth history
        List<AuthorizationLog> settled = authorizationService.getHistory(id).stream()
                .filter(a -> "00".equals(a.getResponseCode()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(settled));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Spending Analytics (API Key)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/cards/{id}/analytics/by-category")
    @PreAuthorize("hasRole('API_KEY') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SpendingAnalyticsService.CategorySummary>>> byCategory(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {

        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate end   = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.byCategory(id, start, end, currency)));
    }

    @GetMapping("/cards/{id}/analytics/by-merchant")
    @PreAuthorize("hasRole('API_KEY') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SpendingAnalyticsService.MerchantSummary>>> byMerchant(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {

        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate end   = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.byMerchant(id, start, end, currency)));
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize("hasRole('API_KEY') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SpendingAnalyticsService.MonthlySummary>> summary(
            @RequestParam UUID cardId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {

        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end   = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.monthlySummary(cardId, start, end, currency)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Webhook Management (API Key)
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/webhooks")
    @PreAuthorize("hasRole('API_KEY') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RegisterWebhookResponse>> registerWebhook(
            @Valid @RequestBody RegisterWebhookRequest req,
            Authentication auth) {

        UUID createdBy = resolveUserId(auth);
        WebhookService.RegisterResult result = webhookService.register(
                req.name(), req.callbackUrl(), req.events(), createdBy);
        // Return secret once — never retrievable again
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new RegisterWebhookResponse(result.webhook(), result.secret())));
    }

    @GetMapping("/webhooks")
    @PreAuthorize("hasRole('API_KEY') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Webhook>>> listWebhooks() {
        return ResponseEntity.ok(ApiResponse.ok(webhookService.listActive()));
    }

    @DeleteMapping("/webhooks/{id}")
    @PreAuthorize("hasRole('API_KEY') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteWebhook(@PathVariable UUID id) {
        webhookService.deregister(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/webhooks/{id}/deliveries")
    @PreAuthorize("hasRole('API_KEY') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<WebhookDeliveryLog>>> listDeliveries(
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.ok(webhookService.listDeliveries(id)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Request / Response DTOs
    // ══════════════════════════════════════════════════════════════════════════

    public record IssueKeyRequest(
            @NotBlank @Size(max = 100) String name,
            List<String> scopes) {}

    public record IssueKeyResponse(ApiKey apiKey, String rawKey) {}

    public record IssueCardRequest(
            UUID productId, UUID customerId, UUID linkedEntityId, Boolean virtual) {}

    public record CardControlsRequest(
            Boolean freeze,
            Boolean contactlessEnabled,
            Boolean cnpEnabled,
            Boolean internationalEnabled) {}

    public record UpdateLimitsRequest(
            BigDecimal dailyPurchaseLimit,
            BigDecimal dailyWithdrawalLimit,
            BigDecimal perTransactionLimit,
            BigDecimal monthlyLimit) {}

    public record PinChangeRequest(
            @NotBlank String oldPinBlock,
            @NotBlank String newPinBlock) {}

    public record RegisterWebhookRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank String callbackUrl,
            List<String> events) {}

    public record RegisterWebhookResponse(Webhook webhook, String secret) {}

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private UUID resolveUserId(Authentication auth) {
        if (auth instanceof ApiKeyAuthentication apiAuth) {
            return (UUID) apiAuth.getPrincipal();
        }
        // JWT: sub claim is the Keycloak user UUID
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
    }
}

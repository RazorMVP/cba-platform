package com.cba.card.card;

import com.cba.card.auth.CardAuthorizationService;
import com.cba.card.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final CardAuthorizationService cardAuthorizationService;

    /** Issue a new card. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<Card>> issueCard(@Valid @RequestBody IssueCardRequest req) {
        Card card = cardService.issueCard(
                req.productId(), req.customerId(), req.linkedEntityId(),
                req.virtual() != null && req.virtual(),
                req.pan(), req.expiryDate(), req.cvv(),
                req.currencyCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(card));
    }

    /** List cards for a customer. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<List<Card>>> listCards(
            @RequestParam(required = false) UUID customerId) {
        List<Card> cards = customerId != null
                ? cardService.findByCustomer(customerId)
                : List.of();
        return ResponseEntity.ok(ApiResponse.ok(cards));
    }

    /** Get a single card. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<Card>> getCard(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(cardService.findById(id)));
    }

    /**
     * Available balance for a card — called by the backend monolith's Open Banking layer.
     * Routes internally to wallet / account / credit-line based on card type.
     */
    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<CardAuthorizationService.BalanceResult>> getBalance(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(cardAuthorizationService.getAvailableBalance(id)));
    }

    /** Lifecycle commands: block, unblock, cancel, activate, replace. */
    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<Card>> command(
            @PathVariable UUID id,
            @RequestParam String command) {
        Card card = cardService.executeCommand(id, command);
        return ResponseEntity.ok(ApiResponse.ok(card));
    }

    /** List card products. Delegates to the dedicated CardProductController. */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<CardProduct>>> listProducts() {
        return ResponseEntity.ok(ApiResponse.ok(List.of())); // product list served by /api/v1/cards/products
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    public record IssueCardRequest(
            @NotNull UUID productId,
            @NotNull UUID customerId,
            UUID linkedEntityId,
            Boolean virtual,
            @NotNull String pan,
            @NotNull String expiryDate,
            @NotNull String cvv,
            /** ISO 4217 numeric currency code for card limits (e.g. "840"=USD, "404"=KES, "288"=GHS). Required. */
            @NotNull String currencyCode) {}
}

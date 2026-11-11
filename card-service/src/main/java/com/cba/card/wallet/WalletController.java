package com.cba.card.wallet;

import com.cba.card.common.ApiResponse;
import com.cba.card.common.CbaException;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Prepaid wallets (backlog 15c): balance and movements, loads from and unloads to the cardholder's backend
 * account. A movement the backend didn't answer comes back 202 PENDING and is retried; a refusal is 400 with
 * the backend's reason (the movement is kept as FAILED).
 */
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    public record LoadRequest(@NotNull UUID sourceAccountId, @NotNull BigDecimal amount, String currencyCode) {}

    public record UnloadRequest(@NotNull UUID destinationAccountId, @NotNull BigDecimal amount) {}

    @GetMapping("/{id}/wallet")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<WalletService.WalletView>> wallet(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.wallet(id)));
    }

    @PostMapping("/{id}/wallet/loads")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<WalletMovement>> load(@PathVariable UUID id,
                                                            @jakarta.validation.Valid @RequestBody LoadRequest req,
                                                            Authentication auth) {
        return answer(walletService.load(id, req.sourceAccountId(), req.amount(), req.currencyCode(), actor(auth)));
    }

    @PostMapping("/{id}/wallet/unloads")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<WalletMovement>> unload(@PathVariable UUID id,
                                                              @jakarta.validation.Valid @RequestBody UnloadRequest req,
                                                              Authentication auth) {
        return answer(walletService.unload(id, req.destinationAccountId(), req.amount(), actor(auth)));
    }

    /** Wallet balances per currency — the backend's GL 2500 reconciliation (service role CARD_READER). */
    @GetMapping("/wallets/totals")
    @PreAuthorize("hasAnyRole('ADMIN','CARD_READER')")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> totals() {
        return ResponseEntity.ok(ApiResponse.ok(walletService.totals()));
    }

    private static ResponseEntity<ApiResponse<WalletMovement>> answer(WalletMovement m) {
        return switch (m.getStatus()) {
            case WalletMovement.COMPLETED -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(m));
            case WalletMovement.FAILED -> throw CbaException.badRequest(m.getFailureReason() == null ? "WALLET_MOVEMENT_REFUSED"
                    : m.getFailureReason(), "The backend refused the " + m.getMovementType().toLowerCase(java.util.Locale.ROOT)
                    + " (" + m.getReference() + ")");
            default -> ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(m));
        };
    }

    private static String actor(Authentication auth) {
        return auth == null ? "system" : auth.getName();
    }
}

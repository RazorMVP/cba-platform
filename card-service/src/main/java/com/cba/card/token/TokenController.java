package com.cba.card.token;

import com.cba.card.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    /** Issue a DPAN token for a card. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<ApiResponse<TokenService.TokenResponse>> tokenize(
            @RequestBody TokenizeRequest req) {
        TokenService.TokenResponse resp = tokenService.tokenize(req.cardId(), req.customerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(resp));
    }

    // No public de-tokenize endpoint: it returned a full PAN and took the DPAN in the URL (access logs).
    // fep-service de-tokenizes through POST /api/v1/internal/detokenize (FEP_SERVICE token only).

    @PostMapping("/{tokenRef}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ResponseEntity<Void> suspend(@PathVariable String tokenRef) {
        tokenService.suspend(tokenRef);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tokenRef}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String tokenRef) {
        tokenService.delete(tokenRef);
        return ResponseEntity.noContent().build();
    }

    public record TokenizeRequest(UUID cardId, UUID customerId) {}
}

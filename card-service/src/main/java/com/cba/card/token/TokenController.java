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

    /**
     * De-tokenize a DPAN back to the real PAN.
     * Called by fep-service via the internal controller — also exposed here for admin use.
     */
    @GetMapping("/detokenize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> detokenize(@RequestParam String dpan) {
        return ResponseEntity.ok(ApiResponse.ok(tokenService.detokenize(dpan)));
    }

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

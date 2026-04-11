package com.cba.card.auth;

import com.cba.card.card.CardService;
import com.cba.card.token.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Internal endpoints called exclusively by fep-service.
 *
 * <p>These are under {@code /api/v1/internal/} which is excluded from JWT
 * validation by {@link com.cba.card.config.SecurityConfig} — protected instead
 * by network isolation (not accessible from outside the service mesh).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class CardAuthorizationController {

    private final CardAuthorizationService authorizationService;
    private final CardService              cardService;
    private final TokenService             tokenService;

    /** Primary authorization endpoint — called by FEP for every 0100/0200 message. */
    @PostMapping("/authorize")
    public ResponseEntity<CardAuthResponse> authorize(@RequestBody CardAuthRequest req) {
        log.debug("Authorization request: STAN={} PAN={}****",
                req.stan(), req.pan() != null && req.pan().length() >= 6 ? req.pan().substring(0, 6) : "??");
        CardAuthResponse response = authorizationService.authorize(req);
        return ResponseEntity.ok(response);
    }

    /** Advice recording — called by FEP for 0120/0220 single-message advice. */
    @PostMapping("/advise")
    public ResponseEntity<CardAuthResponse> recordAdvice(@RequestBody AdviceRequest req) {
        CardAuthResponse response = authorizationService.recordAdvice(req.pan(), req.amount(), req.stan());
        return ResponseEntity.ok(response);
    }

    /** De-tokenization — called by FEP when PAN starts with token BIN prefix (9999xx). */
    @GetMapping("/detokenize")
    public ResponseEntity<DetokenizeResponse> detokenize(@RequestParam String dpan) {
        try {
            String realPan = tokenService.detokenize(dpan);
            return ResponseEntity.ok(new DetokenizeResponse(realPan));
        } catch (Exception e) {
            // Fall back to DPAN itself — FEP will decline with correct RC from card lookup miss
            log.warn("Detokenize failed for DPAN prefix {}****: {}", dpan.substring(0, Math.min(6, dpan.length())), e.getMessage());
            return ResponseEntity.ok(new DetokenizeResponse(dpan));
        }
    }

    /** Authorization history for a card (ADMIN view). */
    @GetMapping("/cards/{cardId}/authorizations")
    public ResponseEntity<List<AuthorizationLog>> getHistory(@PathVariable UUID cardId) {
        return ResponseEntity.ok(authorizationService.getHistory(cardId));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record AdviceRequest(String pan, BigDecimal amount, String stan) {}

    public record DetokenizeResponse(String pan) {}
}

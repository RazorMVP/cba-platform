package com.cba.card.config;

import com.cba.card.integration.AbstractCardIntegrationTest;
import com.cba.card.openbanking.apikey.ApiKeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backlog 20 over real HTTP: the Card API limiter sits after authentication in the real security
 * chain, so the bucket comes from the verified caller — never from an unchecked key or token.
 *
 * <p>Redis isn't running here; {@code RateLimitService} fails open but still reports the tier's
 * limit in {@code X-RateLimit-Limit}, which is what these tests read.
 */
@DisplayName("Card API rate limiting — bucket from the verified caller, over real HTTP")
class RateLimitIntegrationTest extends AbstractCardIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    private static String b64url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<String> listCards(HttpHeaders headers) {
        return rest.exchange("/card-api/v1/cards", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("a valid PRO API key authenticates and gets its tier: 500/min")
    void apiKeyTier() {
        String raw = "cba_rlit_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO api_keys (name, key_hash, created_by, tier) VALUES (?, ?, ?, ?)",
                "rl-it", ApiKeyService.sha256hex(raw), UUID.randomUUID(), "PRO");
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "ApiKey " + raw);

        ResponseEntity<String> r = listCards(h);

        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        assertThat(r.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("500");
    }

    @Test
    @DisplayName("an unknown API key is anonymous: counted per IP at 30/min, then 401 (no fresh bucket)")
    void unknownApiKey() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "ApiKey cba_unknown_" + UUID.randomUUID());

        ResponseEntity<String> r = listCards(h);

        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat(r.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("30");
    }

    @Test
    @DisplayName("a forged bearer token is rejected before it's counted: 401, no rate-limit headers")
    void forgedTokenNotCounted() {
        HttpHeaders h = new HttpHeaders();
        // Unsigned token claiming a subject: the old filter bucketed on this sub without verifying it.
        // Built at runtime so no JWT-shaped literal sits in source (Gitleaks rule `jwt`).
        h.setBearerAuth(b64url("{\"alg\":\"none\"}") + "." + b64url("{\"sub\":\"victim\"}") + ".");

        ResponseEntity<String> r = listCards(h);

        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat(r.getHeaders().getFirst("X-RateLimit-Limit")).isNull();
    }

    @Test
    @DisplayName("a verified Keycloak token is counted per user at BASIC: 100/min")
    void keycloakToken() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(ROLES_TOKEN + ".ADMIN");

        ResponseEntity<String> r = listCards(h);

        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        assertThat(r.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
    }
}

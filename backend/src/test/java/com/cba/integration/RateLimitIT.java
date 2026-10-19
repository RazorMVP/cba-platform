package com.cba.integration;

import com.cba.partner.PartnerApiKeys;
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
 * Backlog 16 + 6 over real HTTP: the rate limiter sits after authentication in the real security
 * chain, so the limit comes from the verified caller — never from {@code X-Rate-Tier} or an
 * unverified token — and a partner gets its organisation's tier.
 *
 * <p>Redis isn't running here; {@code RateLimitService} fails open but still reports the tier's
 * limit in {@code X-RateLimit-Limit}, which is what these tests read.
 */
@DisplayName("Rate limiting — tier from the verified caller, over real HTTP")
class RateLimitIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    private static String b64url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /** A partner organisation with an active API key; returns the raw key. */
    private String partnerKey(UUID org, String tier, String environment) {
        jdbc.update("INSERT INTO partner_organizations (id, name, status, tier, environment) VALUES (?, ?, ?, ?, ?)",
                org, "RL-IT " + org, environment.equals("SANDBOX") ? "SANDBOX" : "PRODUCTION", tier, environment);
        String raw = "cba_rlit_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO partner_api_keys (organization_id, name, key_hash, key_prefix) VALUES (?, ?, ?, ?)",
                org, "rl-it", PartnerApiKeys.hash(raw), raw.substring(0, 12));
        return raw;
    }

    @Test
    @DisplayName("item 16: X-Rate-Tier: ENTERPRISE doesn't raise the limit (100, not 2000)")
    void tierHeaderIgnored() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Rate-Tier", "ENTERPRISE");

        ResponseEntity<String> r = get("/api/v1/glaccounts", h);

        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        assertThat(r.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("100");
    }

    @Test
    @DisplayName("a forged bearer token is rejected before it's counted: 401, no rate-limit headers")
    void forgedTokenNotCounted() {
        HttpHeaders h = new HttpHeaders();
        // Unsigned token claiming a client id: the old filter bucketed on this azp without verifying it.
        // Built at runtime so no JWT-shaped literal sits in source (Gitleaks rule `jwt`).
        h.setBearerAuth(b64url("{\"alg\":\"none\"}") + "." + b64url("{\"azp\":\"forged-client\"}") + ".");
        h.set("X-Rate-Tier", "ENTERPRISE");

        ResponseEntity<String> r = get("/api/v1/glaccounts", h);

        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat(r.getHeaders().getFirst("X-RateLimit-Limit")).isNull();
    }

    @Test
    @DisplayName("item 6: a PRO production partner's API key gets 500/min")
    void partnerProTier() {
        UUID org = UUID.randomUUID();
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "ApiKey " + partnerKey(org, "PRO", "PRODUCTION"));

        ResponseEntity<String> r = get("/api/v1/partners/" + org + "/usage", h);

        assertThat(r.getHeaders().getFirst("X-RateLimit-Limit")).as(r.getBody()).isEqualTo("500");
    }

    @Test
    @DisplayName("a partner API key really authenticates as its org: another org's URL is 404, not the dev-bypass admin's 200")
    void apiKeyAuthenticatesAsPartner() {
        UUID org = UUID.randomUUID();
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "ApiKey " + partnerKey(org, "PRO", "PRODUCTION"));

        ResponseEntity<String> r = get("/api/v1/partners/" + UUID.randomUUID() + "/usage", h);

        // PartnerSecurity.requireOrgAccess: a partner asking for another org's data gets 404.
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(404);
    }

    @Test
    @DisplayName("a sandbox organisation stays at BASIC (100/min) whatever its tier")
    void sandboxPartnerBasic() {
        UUID org = UUID.randomUUID();
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "ApiKey " + partnerKey(org, "ENTERPRISE", "SANDBOX"));

        ResponseEntity<String> r = get("/api/v1/partners/" + org + "/usage", h);

        assertThat(r.getHeaders().getFirst("X-RateLimit-Limit")).as(r.getBody()).isEqualTo("100");
    }
}

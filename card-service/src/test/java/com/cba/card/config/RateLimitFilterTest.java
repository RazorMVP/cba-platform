package com.cba.card.config;

import com.cba.card.openbanking.apikey.ApiKey;
import com.cba.card.openbanking.apikey.ApiKeyAuthentication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Backlog 20: the card-service limiter buckets on the verified caller only. */
@DisplayName("RateLimitFilter — bucket and tier from the verified caller")
class RateLimitFilterTest {

    private final RateLimitService service = mock(RateLimitService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final RateLimitFilter filter = new RateLimitFilter(service, mapper);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt(String azp, String sub) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").claim("x", "y");
        if (azp != null) b.claim("azp", azp);
        if (sub != null) b.subject(sub);
        return b.build();
    }

    @Test
    @DisplayName("no authentication → ip:<remote address> at SANDBOX")
    void anonymousNull() {
        assertThat(filter.resolveCaller(null, "10.0.0.7"))
                .isEqualTo(new RateLimitFilter.Caller("ip:10.0.0.7", RateLimitService.Tier.SANDBOX));
    }

    @Test
    @DisplayName("AnonymousAuthenticationToken (e.g. an unknown API key) → ip bucket at SANDBOX")
    void anonymousToken() {
        var anon = new AnonymousAuthenticationToken("k", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(filter.resolveCaller(anon, "203.0.113.9"))
                .isEqualTo(new RateLimitFilter.Caller("ip:203.0.113.9", RateLimitService.Tier.SANDBOX));
    }

    @Test
    @DisplayName("verified API key → key:<id> at the key's tier")
    void apiKey() {
        ApiKey key = new ApiKey();
        key.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        key.setName("k");
        key.setTier("ENTERPRISE");
        key.setScopes(List.of("CARD_READ"));

        assertThat(filter.resolveCaller(new ApiKeyAuthentication(key), "10.0.0.1")).isEqualTo(
                new RateLimitFilter.Caller("key:11111111-2222-3333-4444-555555555555",
                        RateLimitService.Tier.ENTERPRISE));
    }

    @Test
    @DisplayName("API key with an unknown tier → BASIC")
    void apiKeyUnknownTier() {
        ApiKey key = new ApiKey();
        key.setId(UUID.randomUUID());
        key.setName("k");
        key.setTier("PLATINUM");

        assertThat(filter.resolveCaller(new ApiKeyAuthentication(key), "10.0.0.1").tier())
                .isEqualTo(RateLimitService.Tier.BASIC);
    }

    @Test
    @DisplayName("Keycloak token → jwt:<azp>:<sub> at BASIC; azp-only and sub-only fall back")
    void keycloak() {
        assertThat(filter.resolveCaller(new JwtAuthenticationToken(jwt("cba-web", "u1"), AuthorityUtils.NO_AUTHORITIES), "x").identity())
                .isEqualTo("jwt:cba-web:u1");
        assertThat(filter.resolveCaller(new JwtAuthenticationToken(jwt(null, "u1"), AuthorityUtils.NO_AUTHORITIES), "x").identity())
                .isEqualTo("jwt:u1");
        assertThat(filter.resolveCaller(new JwtAuthenticationToken(jwt("svc", null), AuthorityUtils.NO_AUTHORITIES), "x").identity())
                .isEqualTo("jwt:svc");
        assertThat(filter.resolveCaller(new JwtAuthenticationToken(jwt("cba-web", "u1"), AuthorityUtils.NO_AUTHORITIES), "x").tier())
                .isEqualTo(RateLimitService.Tier.BASIC);
    }

    @Test
    @DisplayName("any other login (dev bypass) → user:<name> at BASIC")
    void otherLogin() {
        var user = new UsernamePasswordAuthenticationToken("dev-admin", null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        assertThat(filter.resolveCaller(user, "x"))
                .isEqualTo(new RateLimitFilter.Caller("user:dev-admin", RateLimitService.Tier.BASIC));
    }

    @Test
    @DisplayName("X-Forwarded-For and the Authorization header are never read")
    void ignoresClientHeaders() throws Exception {
        when(service.check(anyString(), any())).thenReturn(RateLimitResult.allowed(30, 29));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/card-api/v1/cards");
        req.setRemoteAddr("10.1.2.3");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        req.addHeader("Authorization", "ApiKey cba_random_unverified");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        verify(service).check("ip:10.1.2.3", RateLimitService.Tier.SANDBOX);
    }

    @Test
    @DisplayName("OPTIONS and paths outside /card-api/v1/ aren't counted")
    void notCounted() throws Exception {
        filter.doFilter(new MockHttpServletRequest("OPTIONS", "/card-api/v1/cards"),
                new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/cards/products"),
                new MockHttpServletResponse(), new MockFilterChain());

        verify(service, never()).check(anyString(), any());
    }

    @Test
    @DisplayName("over the limit → 429 with the standard envelope, Retry-After 60, chain not called")
    void overLimit() throws Exception {
        when(service.check(anyString(), any())).thenReturn(RateLimitResult.denied(30));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/card-api/v1/cards"), res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
        assertThat(res.getHeader("X-RateLimit-Limit")).isEqualTo("30");
        JsonNode body = mapper.readTree(res.getContentAsString());
        assertThat(body.path("errors").get(0).path("code").asText()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(chain.getRequest()).isNull();
    }
}

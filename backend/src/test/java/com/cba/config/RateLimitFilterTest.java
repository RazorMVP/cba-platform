package com.cba.config;

import com.cba.partner.PartnerPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The limiter keys on the verified login only (backlog 16 + 6): it runs after authentication, so it
 * never reads a client-supplied tier header, an unverified token, or {@code X-Forwarded-For}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter — bucket and tier from the verified caller")
class RateLimitFilterTest {

    @Mock RateLimitService rateLimitService;
    @Mock RateLimitEventNotifier notifier;

    RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimitService, notifier, new ObjectMapper());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.setRemoteAddr("203.0.113.7");
        return r;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, Authentication auth) throws Exception {
        if (auth != null) SecurityContextHolder.getContext().setAuthentication(auth);
        when(rateLimitService.check(eq("backend"), anyString(), any()))
                .thenAnswer(inv -> RateLimitResult.allowed(100, 99));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static Authentication keycloak(String azp, String sub) {
        Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (azp != null) jwt.claim("azp", azp);
        return new JwtAuthenticationToken(jwt.build(), AuthorityUtils.createAuthorityList("ROLE_TELLER"));
    }

    private static Authentication partnerApiKey(UUID org, String tier, String environment) {
        var auth = new UsernamePasswordAuthenticationToken(org.toString(), null,
                AuthorityUtils.createAuthorityList("ROLE_PARTNER_DEVELOPER", "ROLE_API_CLIENT"));
        auth.setDetails(new PartnerPrincipal(org.toString(), "DEVELOPER", environment, tier, List.of()));
        return auth;
    }

    private static Authentication partnerJwt(UUID org, String tier, String environment) {
        var auth = new UsernamePasswordAuthenticationToken("user-1", null,
                AuthorityUtils.createAuthorityList("ROLE_PARTNER_ADMIN"));
        auth.setDetails(new JWTClaimsSet.Builder().subject("user-1")
                .claim("orgId", org.toString()).claim("tier", tier).claim("environment", environment).build());
        return auth;
    }

    @Test
    @DisplayName("item 16: X-Rate-Tier is ignored — a Keycloak caller stays BASIC")
    void tierHeaderIgnored() throws Exception {
        MockHttpServletRequest r = get("/api/v1/accounts");
        r.addHeader("X-Rate-Tier", "ENTERPRISE");

        run(r, keycloak("tpp-client", "user-9"));

        verify(rateLimitService).check("backend", "jwt:tpp-client", RateLimitService.Tier.BASIC);
    }

    @Test
    @DisplayName("Keycloak token without azp → bucket on the verified sub")
    void keycloakSubFallback() throws Exception {
        run(get("/api/v1/accounts"), keycloak(null, "user-9"));

        verify(rateLimitService).check("backend", "jwt:user-9", RateLimitService.Tier.BASIC);
    }

    @Test
    @DisplayName("anonymous → SANDBOX on the connection address; X-Forwarded-For is not read here")
    void anonymousByRemoteAddr() throws Exception {
        MockHttpServletRequest r = get("/api/v1/partners/auth/login");
        r.addHeader("X-Forwarded-For", "198.51.100.1");
        r.addHeader("X-Rate-Tier", "ENTERPRISE");

        run(r, new AnonymousAuthenticationToken("k", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        verify(rateLimitService).check("backend", "ip:203.0.113.7", RateLimitService.Tier.SANDBOX);
    }

    @Test
    @DisplayName("no authentication at all → treated as anonymous")
    void noAuthentication() throws Exception {
        run(get("/api/v1/accounts"), null);

        verify(rateLimitService).check("backend", "ip:203.0.113.7", RateLimitService.Tier.SANDBOX);
    }

    @Test
    @DisplayName("item 6: partner API key → the organisation's bucket and tier")
    void partnerApiKeyTier() throws Exception {
        UUID org = UUID.randomUUID();

        run(get("/api/v1/partners/" + org + "/usage"), partnerApiKey(org, "PRO", "PRODUCTION"));

        verify(rateLimitService).check("backend", "partner:" + org, RateLimitService.Tier.PRO);
    }

    @Test
    @DisplayName("item 6: partner JWT → the same organisation bucket as its API keys")
    void partnerJwtTier() throws Exception {
        UUID org = UUID.randomUUID();

        run(get("/api/v1/partners/" + org + "/usage"), partnerJwt(org, "ENTERPRISE", "PRODUCTION"));

        verify(rateLimitService).check("backend", "partner:" + org, RateLimitService.Tier.ENTERPRISE);
    }

    @Test
    @DisplayName("a sandbox organisation gets BASIC whatever its tier")
    void sandboxOrgIsBasic() throws Exception {
        UUID org = UUID.randomUUID();

        run(get("/api/v1/partners/" + org + "/usage"), partnerApiKey(org, "ENTERPRISE", "SANDBOX"));

        verify(rateLimitService).check("backend", "partner:" + org, RateLimitService.Tier.BASIC);
    }

    @Test
    @DisplayName("an unknown tier value falls back to BASIC")
    void unknownTierIsBasic() throws Exception {
        UUID org = UUID.randomUUID();

        run(get("/api/v1/partners/" + org + "/usage"), partnerApiKey(org, "PLATINUM", "PRODUCTION"));

        verify(rateLimitService).check("backend", "partner:" + org, RateLimitService.Tier.BASIC);
    }

    @Test
    @DisplayName("any other login (e.g. dev bypass) → bucket on its name, BASIC")
    void otherLogin() throws Exception {
        run(get("/api/v1/accounts"), new UsernamePasswordAuthenticationToken("dev-bypass-admin", null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        verify(rateLimitService).check("backend", "user:dev-bypass-admin", RateLimitService.Tier.BASIC);
    }

    @Test
    @DisplayName("the notifier gets the verified org (null for non-partners)")
    void notifierGetsVerifiedOrg() throws Exception {
        UUID org = UUID.randomUUID();
        MockHttpServletRequest r = get("/api/v1/partners/" + org + "/usage");
        run(r, partnerApiKey(org, "PRO", "PRODUCTION"));
        verify(notifier).maybeNotify(eq(r), eq("partner:" + org), eq(org), any());

        SecurityContextHolder.clearContext();
        MockHttpServletRequest r2 = get("/api/v1/accounts");
        run(r2, keycloak("tpp-client", "u"));
        verify(notifier).maybeNotify(eq(r2), eq("jwt:tpp-client"), isNull(), any());
    }

    @Test
    @DisplayName("CORS preflight (OPTIONS) is not counted")
    void preflightNotCounted() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("OPTIONS", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(r, response, new MockFilterChain());

        verify(rateLimitService, never()).check(anyString(), anyString(), any());
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }

    @Test
    @DisplayName("over the limit → 429 RATE_LIMIT_EXCEEDED and the chain stops")
    void denied() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(keycloak("tpp-client", "u"));
        when(rateLimitService.check(eq("backend"), anyString(), any())).thenReturn(RateLimitResult.denied(100));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(get("/api/v1/accounts"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(chain.getRequest()).isNull();
    }
}

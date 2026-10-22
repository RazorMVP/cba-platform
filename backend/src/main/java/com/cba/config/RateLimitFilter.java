package com.cba.config;

import com.cba.common.response.ApiResponse;
import com.cba.partner.PartnerOrganizationRepository;
import com.cba.partner.PartnerPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Applies per-caller rate limiting to Open Banking and partner API endpoints.
 *
 * <p><b>Runs after authentication</b> ({@code SecurityConfig} places it just before
 * {@code AuthorizationFilter}), so the bucket and tier come only from the verified login — never
 * from a client-supplied header or an unverified token (backlog 16):
 * <ul>
 *   <li>Partner API key or partner JWT → {@code partner:<orgId>} (one bucket per organisation) at the
 *       organisation's current tier, read from the database; a SANDBOX-environment organisation
 *       gets BASIC (backlog 6)
 *   <li>Keycloak token → {@code jwt:<azp>:<sub>} (per user within the client) at BASIC
 *   <li>Anonymous → {@code ip:<remote address>} at SANDBOX. The remote address is the client's only
 *       when {@code X-Forwarded-For} came from a trusted proxy ({@code server.forward-headers-strategy:
 *       native}); this filter never reads that header itself
 * </ul>
 * A request whose token fails verification is rejected (401) before it reaches this filter.
 *
 * <p><b>Fail-open:</b> If Redis is unavailable, the filter passes the request
 * through — rate limiting degrades gracefully rather than blocking all traffic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /** Paths that are rate-limited. Everything else is passed through. */
    private static final List<String> RATE_LIMITED_PREFIXES = List.of(
            "/open-banking/v3.1/",
            "/api/v1/"
    );

    /** Paths always excluded (docs, health, auth). */
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs",
            "/actuator",
            "/login",
            "/api/v1/sandbox/"
    );

    private final RateLimitService rateLimitService;
    private final RateLimitEventNotifier rateLimitEventNotifier;
    private final ObjectMapper objectMapper;
    private final PartnerOrganizationRepository organizations;

    /** Who is being limited: the bucket key, the tier, and the partner org (null for non-partners). */
    record Caller(String identity, RateLimitService.Tier tier, UUID orgId) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!isRateLimited(request)) {
            chain.doFilter(request, response);
            return;
        }

        Caller caller = resolveCaller(SecurityContextHolder.getContext().getAuthentication(),
                request.getRemoteAddr());
        RateLimitResult result = rateLimitService.check("backend", caller.identity(), caller.tier());

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(
                System.currentTimeMillis() / 1000 + 60));

        // Best-effort partner webhook on WARNING/EXCEEDED (no-op unless partner-attributable + threshold crossed).
        rateLimitEventNotifier.maybeNotify(request, caller.identity(), caller.orgId(), result);

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", "60");

            // Standard envelope. This used Map.of(... null ...), which throws NullPointerException,
            // so an over-limit request never got its 429 (cont. 43).
            String body = objectMapper.writeValueAsString(ApiResponse.error("RATE_LIMIT_EXCEEDED",
                    "Too many requests. Limit: " + result.limit() + " req/min. Retry after 60 seconds."));
            response.getWriter().write(body);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * card-service's debit-card balance check runs inside every card authorization, all under one
     * service identity: limiting it would turn card traffic into RC 91 declines. It is a cheap read
     * behind the CARD_SERVICE role.
     */
    private static final java.util.regex.Pattern CARD_FUNDS_PATH =
            java.util.regex.Pattern.compile("^/api/v1/accounts/[^/]+/(balance|card-debits|card-holds(/[^/]+/(release|capture))?)$");

    private boolean isRateLimited(HttpServletRequest request) {
        // CORS preflights carry no credentials and do nothing; counting them would spend the
        // anonymous per-IP budget of every browser user.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith) || CARD_FUNDS_PATH.matcher(path).matches()) {
            return false;
        }
        return RATE_LIMITED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /** Bucket and tier from the verified authentication (see the class comment). */
    Caller resolveCaller(Authentication auth, String remoteAddr) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new Caller("ip:" + remoteAddr, RateLimitService.Tier.SANDBOX, null);
        }
        Object details = auth.getDetails();
        if (details instanceof PartnerPrincipal pp) {
            // API key: tier and environment were read with the key, on this request.
            return partner(pp.orgId(), pp.tier(), pp.environment(), auth);
        }
        if (details instanceof JWTClaimsSet claims) {
            return partnerLogin(claim(claims, "orgId"), auth);
        }
        if (auth instanceof JwtAuthenticationToken jwt) {
            // Per user within the client application. Bucketing on the client alone let one user
            // (any customer on cba-mobile, any teller on cba-web) spend everyone's limit. A
            // client-credentials token's sub is that client's service account, so it stays per client.
            String azp = jwt.getToken().getClaimAsString("azp");
            String sub = jwt.getToken().getSubject();
            String id = azp == null || azp.isBlank() ? sub : sub == null ? azp : azp + ":" + sub;
            return new Caller("jwt:" + id, RateLimitService.Tier.BASIC, null);
        }
        return new Caller("user:" + auth.getName(), RateLimitService.Tier.BASIC, null);
    }

    /**
     * Partner portal login. Its JWT's {@code tier} / {@code environment} claims were set at login and
     * last 24 h, so a downgrade wouldn't apply until expiry: read the organisation's current values.
     * Only the {@code orgId} claim (verified, never changes) is taken from the token.
     */
    private Caller partnerLogin(String orgIdClaim, Authentication auth) {
        UUID org = parseUuid(orgIdClaim);
        if (org == null) {
            return new Caller("user:" + auth.getName(), RateLimitService.Tier.BASIC, null);
        }
        try {
            return organizations.findById(org)
                    .map(o -> partner(org.toString(), o.getTier(),
                            o.getEnvironment() != null ? o.getEnvironment().name() : null, auth))
                    .orElseGet(() -> new Caller("partner:" + org, RateLimitService.Tier.BASIC, org));
        } catch (DataAccessException e) {
            // Never grant more than BASIC on a failed lookup.
            log.warn("Rate limit: partner org {} lookup failed, using BASIC: {}", org, e.getMessage());
            return new Caller("partner:" + org, RateLimitService.Tier.BASIC, org);
        }
    }

    /** One bucket per organisation, at its tier; sandbox organisations always get BASIC. */
    private static Caller partner(String orgId, String tier, String environment, Authentication auth) {
        UUID org = parseUuid(orgId);
        if (org == null) {
            return new Caller("user:" + auth.getName(), RateLimitService.Tier.BASIC, null);
        }
        RateLimitService.Tier effective = "SANDBOX".equalsIgnoreCase(environment)
                ? RateLimitService.Tier.BASIC
                : parseTier(tier);
        return new Caller("partner:" + org, effective, org);
    }

    private static RateLimitService.Tier parseTier(String tier) {
        if (tier == null || tier.isBlank()) return RateLimitService.Tier.BASIC;
        try {
            return RateLimitService.Tier.valueOf(tier.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RateLimitService.Tier.BASIC;
        }
    }

    private static String claim(JWTClaimsSet claims, String name) {
        Object v = claims.getClaim(name);
        return v != null ? v.toString() : null;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

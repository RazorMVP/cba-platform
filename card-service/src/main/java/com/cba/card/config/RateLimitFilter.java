package com.cba.card.config;

import com.cba.card.common.ApiResponse;
import com.cba.card.openbanking.apikey.ApiKeyAuthentication;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

/**
 * Applies per-caller rate limiting to the Card API ({@code /card-api/v1/**}).
 *
 * <p><b>Runs after authentication</b> ({@code SecurityConfig} places it just before
 * {@code AuthorizationFilter}), so the bucket comes only from the verified login — never from a
 * key or token nobody has checked (backlog 20; the backend got the same fix in cont. 43):
 * <ul>
 *   <li>API key → {@code key:<apiKeyId>} at the key's {@code api_keys.tier}, read with the key on
 *       this request
 *   <li>Keycloak token → {@code jwt:<azp>:<sub>} (per user within the client) at BASIC
 *   <li>Anonymous — including an unknown API key → {@code ip:<remote address>} at SANDBOX. The remote
 *       address is the client's only when {@code X-Forwarded-For} came from a trusted proxy
 *       ({@code server.forward-headers-strategy: native}); this filter never reads that header itself
 * </ul>
 * A request whose bearer token fails verification is rejected (401) before it reaches this filter.
 *
 * <p>Fail-open: Redis unavailability passes the request through.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> RATE_LIMITED_PREFIXES = List.of("/card-api/v1/");
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/v3/api-docs", "/swagger-ui", "/actuator", "/3ds/acs/"
    );

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    /** Who is being limited: the bucket key and the tier. */
    record Caller(String identity, RateLimitService.Tier tier) {}

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
        RateLimitResult result = rateLimitService.check(caller.identity(), caller.tier());

        response.setHeader("X-RateLimit-Limit",     String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset",
                String.valueOf(System.currentTimeMillis() / 1000 + 60));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", "60");
            // Standard envelope. This used Map.of(... null ...), which throws NullPointerException,
            // so an over-limit request got a 500 instead of its 429.
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(
                    "RATE_LIMIT_EXCEEDED",
                    "Too many requests. Limit: " + result.limit() + " req/min. Retry after 60 seconds.")));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(HttpServletRequest request) {
        // CORS preflights carry no credentials and do nothing; counting them would spend the
        // anonymous per-IP budget of every browser user.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) return false;
        return RATE_LIMITED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /** Bucket and tier from the verified authentication (see the class comment). */
    Caller resolveCaller(Authentication auth, String remoteAddr) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new Caller("ip:" + remoteAddr, RateLimitService.Tier.SANDBOX);
        }
        if (auth instanceof ApiKeyAuthentication key) {
            return new Caller("key:" + key.getPrincipal(), RateLimitService.Tier.fromString(key.getTier()));
        }
        if (auth instanceof JwtAuthenticationToken jwt) {
            // Per user within the client application; a client-credentials token's sub is that
            // client's service account, so it stays per client.
            String azp = jwt.getToken().getClaimAsString("azp");
            String sub = jwt.getToken().getSubject();
            String id = azp == null || azp.isBlank() ? sub : sub == null ? azp : azp + ":" + sub;
            return new Caller("jwt:" + id, RateLimitService.Tier.BASIC);
        }
        return new Caller("user:" + auth.getName(), RateLimitService.Tier.BASIC);
    }
}

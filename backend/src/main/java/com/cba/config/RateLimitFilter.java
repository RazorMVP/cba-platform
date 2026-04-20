package com.cba.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Applies per-caller rate limiting to Open Banking and partner API endpoints.
 *
 * <p><b>Identity resolution order:</b>
 * <ol>
 *   <li>JWT {@code azp} claim (Keycloak client ID — identifies the TPP/fintech)
 *   <li>JWT {@code sub} claim (user subject — for user-token flows)
 *   <li>Remote IP address (fallback for unauthenticated public endpoints)
 * </ol>
 *
 * <p><b>Tier:</b> All Open Banking callers use BASIC (100 req/min) by default.
 * The tier system is extended by the Partner Management module (Session 106+).
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
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!isRateLimited(path)) {
            chain.doFilter(request, response);
            return;
        }

        String identity = resolveIdentity(request);
        RateLimitService.Tier tier = resolveTier(request);
        RateLimitResult result = rateLimitService.check("backend", identity, tier);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(
                System.currentTimeMillis() / 1000 + 60));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", "60");

            String body = objectMapper.writeValueAsString(Map.of(
                    "data", (Object) null,
                    "meta", Map.of(),
                    "errors", List.of(Map.of(
                            "code", "RATE_LIMIT_EXCEEDED",
                            "message", "Too many requests. Limit: " + result.limit()
                                    + " req/min. Retry after 60 seconds.",
                            "field", (Object) null
                    ))
            ));
            response.getWriter().write(body);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String path) {
        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return false;
        }
        return RATE_LIMITED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * Extracts a stable caller identity from the JWT without verifying its
     * signature (verification happens downstream in the security filter chain).
     * Falls back to remote IP for public endpoints.
     */
    private String resolveIdentity(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            String azp = extractJwtClaim(token, "azp");
            if (azp != null && !azp.isBlank()) return "jwt:" + azp;
            String sub = extractJwtClaim(token, "sub");
            if (sub != null && !sub.isBlank()) return "jwt:" + sub;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return "ip:" + ip.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private RateLimitService.Tier resolveTier(HttpServletRequest request) {
        // Partner Management (Session 106) will inject a tier header after
        // API key lookup. For now all callers use BASIC.
        String tierHeader = request.getHeader("X-Rate-Tier");
        if (tierHeader != null) {
            try { return RateLimitService.Tier.valueOf(tierHeader.toUpperCase()); }
            catch (IllegalArgumentException ignored) { }
        }
        return RateLimitService.Tier.BASIC;
    }

    /** Parses a single claim from the JWT payload without signature verification. */
    private String extractJwtClaim(String token, String claim) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // Simple string extraction — avoids ObjectMapper for performance
            String search = "\"" + claim + "\":\"";
            int start = payload.indexOf(search);
            if (start < 0) return null;
            start += search.length();
            int end = payload.indexOf('"', start);
            return end > start ? payload.substring(start, end) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

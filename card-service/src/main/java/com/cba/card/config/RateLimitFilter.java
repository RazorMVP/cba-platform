package com.cba.card.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Applies per-API-key (or per-IP) rate limiting to the Card API.
 *
 * <p>The filter resolves tier from the {@code api_keys.tier} column so that
 * SANDBOX keys (30 req/min) are automatically restricted relative to
 * production BASIC (100), PRO (500), and ENTERPRISE (2000) keys.
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!isRateLimited(path)) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitResult result = checkLimit(request);

        response.setHeader("X-RateLimit-Limit",     String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset",
                String.valueOf(System.currentTimeMillis() / 1000 + 60));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", "60");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "data",   (Object) null,
                    "meta",   Map.of(),
                    "errors", List.of(Map.of(
                            "code",    "RATE_LIMIT_EXCEEDED",
                            "message", "Too many requests. Limit: " + result.limit()
                                       + " req/min. Retry after 60 seconds.",
                            "field",   (Object) null
                    ))
            )));
            return;
        }

        chain.doFilter(request, response);
    }

    private RateLimitResult checkLimit(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");

        if (auth != null && auth.startsWith("ApiKey ")) {
            String rawKey = auth.substring(7).trim();
            try {
                byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                        .digest(rawKey.getBytes(StandardCharsets.UTF_8));
                String keyHash = HexFormat.of().formatHex(hashBytes);
                return rateLimitService.checkByKeyHash(keyHash);
            } catch (Exception e) {
                return rateLimitService.checkByIp(resolveIp(request));
            }
        }

        if (auth != null && auth.startsWith("Bearer ")) {
            String sub = extractJwtSub(auth.substring(7));
            if (sub != null) return rateLimitService.checkBySubject(sub);
        }

        return rateLimitService.checkByIp(resolveIp(request));
    }

    private boolean isRateLimited(String path) {
        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) return false;
        return RATE_LIMITED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private String extractJwtSub(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(
                    java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String search = "\"sub\":\"";
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

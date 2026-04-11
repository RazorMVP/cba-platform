package com.cba.card.openbanking.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: ApiKey {rawKey}} from the request.
 * If found and valid, sets {@link ApiKeyAuthentication} in the SecurityContext
 * so downstream security checks see an authenticated principal.
 *
 * <p>This filter runs before the JWT bearer filter. If the SecurityContext is
 * already populated (by JWT), the filter is a no-op. Both auth paths coexist
 * on the same {@code /card-api/v1/**} chain.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "ApiKey ";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Skip if already authenticated (e.g. by JWT filter on a previous chain)
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX)) {
            String rawKey = header.substring(PREFIX.length()).trim();
            apiKeyService.verify(rawKey).ifPresent(key -> {
                ApiKeyAuthentication auth = new ApiKeyAuthentication(key);
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        chain.doFilter(request, response);
    }
}

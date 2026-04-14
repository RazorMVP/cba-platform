package com.cba.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Dev-only auth bypass filter.
 *
 * Active only when {@code app.auth-bypass=true} (dev profile).
 * If there is no existing authentication in the SecurityContext, this filter
 * injects a fake ADMIN+TELLER+CUSTOMER principal so that Spring Security's
 * {@code @PreAuthorize} checks pass without a real Keycloak token.
 *
 * This bean does NOT exist in production — {@code @ConditionalOnProperty}
 * ensures it is never instantiated when the property is absent or false.
 */
@Component
@ConditionalOnProperty(name = "app.auth-bypass", havingValue = "true")
public class DevAuthBypassFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DevAuthBypassFilter.class);

    private static final List<SimpleGrantedAuthority> DEV_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ROLE_TELLER"),
            new SimpleGrantedAuthority("ROLE_CUSTOMER"),
            new SimpleGrantedAuthority("ROLE_API_CLIENT")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Only inject if no authentication already present (real token takes priority)
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var auth = new UsernamePasswordAuthenticationToken(
                    "dev-bypass-admin", null, DEV_AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("DevAuthBypassFilter: injected dev ADMIN authentication for {}",
                    request.getRequestURI());
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Let public endpoints pass through without touching the SecurityContext
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/api-docs")
                || uri.startsWith("/v3/api-docs");
    }
}

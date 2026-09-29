package com.cba.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;

/**
 * Rejects requests that use the DPoP authorization scheme, before Spring Security sees them.
 *
 * <p>Spring Security 6.5 wires DPoP authentication into every {@code oauth2ResourceServer()}
 * whenever {@code DPoPProofJwtDecoderFactory} is on the classpath (it ships in
 * spring-security-oauth2-jose), with no switch to turn it off. That factory's replay cache
 * can be flooded so an intercepted proof is accepted twice (CVE-2026-41707, CVSS 7.4). The
 * fix ships only in Spring Security 7.0.6.1, which needs Spring Boot 4.
 *
 * <p>This platform issues no DPoP-bound tokens (the Keycloak realm has no DPoP client), so no
 * legitimate caller sends {@code Authorization: DPoP}. Rejecting it keeps the vulnerable code
 * from ever receiving input — the compensating control behind the CVE-2026-41707 entry in
 * {@code docs/owasp-suppressions.xml}. Remove both once Spring Security is upgraded.
 */
public class DPoPRejectionFilter extends OncePerRequestFilter {

    static final String BODY = "{\"data\":null,\"meta\":{},\"errors\":[{\"code\":\"DPOP_NOT_SUPPORTED\","
            + "\"message\":\"The DPoP authorization scheme is not supported; use Bearer\",\"field\":null}]}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (usesDPoPScheme(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer error=\"invalid_request\", error_description=\"DPoP is not supported\"");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(BODY);
            return;
        }
        chain.doFilter(request, response);
    }

    /** True when any Authorization header starts with "DPoP" (Spring's own matcher is case-insensitive). */
    static boolean usesDPoPScheme(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (values == null) return false;
        while (values.hasMoreElements()) {
            String value = values.nextElement();
            if (value != null && value.stripLeading().regionMatches(true, 0, "DPoP", 0, 4)) {
                return true;
            }
        }
        return false;
    }

    /** Registered ahead of Spring Security's filter chain (order -100) for every path. */
    @Configuration
    static class Registration {
        @Bean
        FilterRegistrationBean<DPoPRejectionFilter> dPoPRejectionFilterRegistration() {
            FilterRegistrationBean<DPoPRejectionFilter> registration =
                    new FilterRegistrationBean<>(new DPoPRejectionFilter());
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            registration.addUrlPatterns("/*");
            return registration;
        }
    }
}

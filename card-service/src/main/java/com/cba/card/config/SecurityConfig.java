package com.cba.card.config;

import com.cba.card.openbanking.apikey.ApiKeyAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Multi-chain security config for card-service:
 *
 * <ol>
 *   <li>Order 0 — 3DS ACS ({@code /3ds/acs/**}): open, no JWT, no rate limit</li>
 *   <li>Order 1 — Internal FEP ({@code /api/v1/internal/**}): open, no JWT, no rate limit</li>
 *   <li>Order 2 — Card API ({@code /card-api/v1/**}): ApiKey or JWT + rate limiting</li>
 *   <li>Order 3 — All others: JWT + rate limiting</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Rate limiting runs on chains 2 and 3 — before auth so unauthenticated abuse is still throttled. */
    @Autowired
    private RateLimitFilter rateLimitFilter;

    /**
     * Dev-only bypass — injected ONLY when {@code app.auth-bypass=true} (local dev).
     * Absent in production (the {@code @ConditionalOnProperty} bean is never created),
     * so this field stays {@code null} and the wiring below is a no-op.
     */
    @Autowired(required = false)
    private DevAuthBypassFilter devAuthBypassFilter;

    /**
     * WebClient bean used by {@link com.cba.card.openbanking.webhook.WebhookDeliveryService}
     * for async webhook delivery. Shared singleton — WebClient is thread-safe.
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1 MB
                .build();
    }

    /**
     * 3DS ACS endpoints — no JWT.
     *
     * <p>Called by the scheme Directory Server (mTLS client cert in production)
     * and by the cardholder's browser (no auth). Must not require a Keycloak token.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain threeDsChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/3ds/acs/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /** Internal endpoints called by FEP — no JWT, network-isolation is the guard. */
    @Bean
    @Order(1)
    public SecurityFilterChain internalChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/internal/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Card Open Banking API ({@code /card-api/v1/**}) — dual-mode auth.
     *
     * <p>Supports either:
     * <ul>
     *   <li>{@code Authorization: ApiKey cba_...} — M2M integrator; handled by
     *       {@link ApiKeyAuthFilter} which sets {@link com.cba.card.openbanking.apikey.ApiKeyAuthentication}</li>
     *   <li>{@code Authorization: Bearer {jwt}} — FAPI 2.0 customer consent;
     *       handled by the standard oauth2ResourceServer JWT filter</li>
     * </ul>
     * Route-level {@code @PreAuthorize} in {@link com.cba.card.openbanking.CardApiController}
     * distinguishes which roles are required per endpoint.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain cardApiChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter)
            throws Exception {
        http
            .securityMatcher("/card-api/v1/**")
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        // Dev-only: inject ADMIN when no ApiKey/JWT present (added after the ApiKey filter so a real key wins).
        if (devAuthBypassFilter != null) {
            http.addFilterBefore(devAuthBypassFilter, UsernamePasswordAuthenticationFilter.class);
        }
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }

    /** All other endpoints — JWT required + rate limited. */
    @Bean
    @Order(3)
    public SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        // Dev-only: inject ADMIN when no Bearer token present so authBypass-mode Angular can read /api/v1/cards/**.
        if (devAuthBypassFilter != null) {
            http.addFilterBefore(devAuthBypassFilter, UsernamePasswordAuthenticationFilter.class);
        }
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // OpenAPI / Swagger UI — documentation is always public
                .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**",
                        "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/simulate/**").hasAnyRole("ADMIN", "TELLER")
                .requestMatchers("/api/v1/simulate/**").hasAnyRole("ADMIN", "TELLER")
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }
}

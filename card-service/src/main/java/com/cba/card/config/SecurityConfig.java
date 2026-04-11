package com.cba.card.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Triple security filter chain:
 *
 * <ol>
 *   <li>Order 0 — 3DS ACS endpoints ({@code /3ds/acs/**}): open to Directory Server
 *       (mTLS in prod) and cardholder browsers. No JWT.</li>
 *   <li>Order 1 — Internal endpoints ({@code /api/v1/internal/**}): open to
 *       network-isolated callers (FEP). No JWT required. In production this path
 *       is protected at the network/ingress level, not at the application layer.</li>
 *   <li>Order 2 — All other endpoints: require valid JWT from Keycloak realm "cba".</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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

    /** Public/BaaS endpoints — JWT required. */
    @Bean
    @Order(2)
    public SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/simulate/**").hasAnyRole("ADMIN", "TELLER")
                .requestMatchers("/api/v1/simulate/**").hasAnyRole("ADMIN", "TELLER")
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }
}

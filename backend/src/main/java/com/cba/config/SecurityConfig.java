package com.cba.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Injected only when app.auth-bypass=true (dev mode). Absent in production. */
    @Autowired(required = false)
    private DevAuthBypassFilter devAuthBypassFilter;

    /** Rate limit filter — runs before any auth filter on every request. */
    @Autowired
    private RateLimitFilter rateLimitFilter;

    /** Partner JWT filter — validates HMAC partner tokens for /api/v1/partners/** */
    @Autowired
    private com.cba.partner.PartnerJwtFilter partnerJwtFilter;

    /** Partner API-key filter — authenticates machine-to-machine "Authorization: ApiKey ..." requests */
    @Autowired
    private com.cba.partner.PartnerApiKeyAuthFilter partnerApiKeyAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Rate limiting runs first — before auth so we can still throttle unauthenticated abuse
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        // Partner JWT filter runs before Keycloak JWT processing
        http.addFilterBefore(partnerJwtFilter, UsernamePasswordAuthenticationFilter.class);
        // Partner API-key (M2M) filter — authenticates "Authorization: ApiKey ..." requests
        http.addFilterBefore(partnerApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (devAuthBypassFilter != null) {
            http.addFilterBefore(devAuthBypassFilter, UsernamePasswordAuthenticationFilter.class);
        }

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // CORS preflight — must be permitted before any auth check
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Partner portal public endpoints
                .requestMatchers(
                    "/api/v1/partners/register",
                    "/api/v1/partners/auth/login"
                ).permitAll()

                // Partner — bank-staff operations (Keycloak ADMIN only; namespaced partner
                // roles cannot match ROLE_ADMIN, so a partner admin cannot self-approve)
                .requestMatchers(HttpMethod.GET, "/api/v1/partners").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/partners/usage").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/partners/*/approve").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/partners/*/reject").hasRole("ADMIN")

                // Partner — developer self-service (partner JWT or API key; ADMIN = staff/dev-bypass override)
                .requestMatchers("/api/v1/partners/**")
                    .hasAnyRole("PARTNER_DEVELOPER", "PARTNER_ADMIN", "ADMIN")

                // Public endpoints — health, docs
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // Open Banking — customer + TPP access
                .requestMatchers("/open-banking/**")
                    .hasAnyRole("CUSTOMER", "API_CLIENT")

                // Read-only access for all authenticated roles
                .requestMatchers(HttpMethod.GET, "/api/v1/**")
                    .hasAnyRole("ADMIN", "TELLER", "CUSTOMER")

                // Write operations require ADMIN or TELLER
                .requestMatchers(HttpMethod.POST, "/api/v1/**")
                    .hasAnyRole("ADMIN", "TELLER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/**")
                    .hasAnyRole("ADMIN", "TELLER")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/**")
                    .hasAnyRole("ADMIN", "TELLER")

                // Admin-only operations
                .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .headers(headers -> headers
                .frameOptions(FrameOptionsConfig::deny)
                // X-XSS-Protection header removed in Spring Security 6.1 — handled by CSP below
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'self'; frame-ancestors 'none'")
                )
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        // Keycloak puts roles inside realm_access.roles
        converter.setAuthoritiesClaimName("realm_access.roles");
        converter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:4200",    // Angular dev server (legacy)
            "http://localhost:3000",    // Partner portal dev server (Vite)
            "http://localhost:5173",    // React dev server (Vite)
            "https://*.cba.com",       // Production domains
            "https://*.vercel.app"     // Vercel preview deployments
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Accept",
            "X-Requested-With", "X-FAPI-Interaction-ID", "X-FAPI-Auth-Date"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

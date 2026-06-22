package com.cba.card.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for card-service integration tests.
 *
 * <p>Starts a real PostgreSQL 16 container via Testcontainers (same as the
 * backend's {@code AbstractIntegrationTest} pattern). Flyway runs all card-service
 * migrations (V1–Vn) automatically on context startup.
 *
 * <p>The inner {@link TestSecurityConfig} supplies a {@code @Primary}
 * {@link JwtDecoder} bean that immediately rejects all tokens — this prevents
 * Spring Boot from contacting Keycloak at startup while still allowing
 * the security filter chain to be configured correctly. Integration tests
 * that need authenticated endpoints should use {@code @WithMockUser} or
 * set the {@code Authorization} header directly on the {@link TestRestTemplate}.
 *
 * <p>OpenAPI snapshot tests only call the public {@code /v3/api-docs.yaml}
 * endpoint, which is permitted without authentication (see {@code SecurityConfig}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractCardIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("card_test")
            .withUsername("card_user")
            .withPassword("card_pass")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Do NOT set spring.flyway.url on its own — that makes Flyway open a separate
        // credential-less connection (SCRAM auth failure). Leaving it unset lets Flyway
        // inherit the datasource url+username+password above.
    }

    /**
     * Replaces the auto-configured {@link JwtDecoder} with a no-op stub for
     * tests. No JWT validation is performed — the stub rejects all tokens,
     * which is correct since integration tests only call public endpoints or
     * mock authentication via Spring Security test support.
     */
    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            // Reject every token — Keycloak is not running in tests.
            // Public endpoints (e.g. /v3/api-docs.yaml) don't reach this decoder.
            return token -> { throw new BadJwtException("JWT validation disabled in tests"); };
        }
    }
}

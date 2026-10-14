package com.cba.card.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Base class for card-service integration tests.
 *
 * <p>Starts a real PostgreSQL 16 container via Testcontainers (same as the
 * backend's {@code AbstractIntegrationTest} pattern). Flyway runs all card-service
 * migrations (V1–Vn) automatically on context startup.
 *
 * <p>The inner {@link TestSecurityConfig} (imported explicitly — a nested
 * {@code @TestConfiguration} of a superclass is not picked up for subclasses) supplies a
 * {@code @Primary} {@link JwtDecoder} so Spring Boot never contacts Keycloak. It rejects every
 * token except {@link #ROLES_TOKEN} ones ({@code Bearer test-roles.ADMIN.TELLER}), which decode to
 * a Keycloak-shaped JWT ({@code realm_access.roles}) so tests go through the real role converter.
 *
 * <p>OpenAPI snapshot tests only call the public {@code /v3/api-docs.yaml}
 * endpoint, which is permitted without authentication (see {@code SecurityConfig}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AbstractCardIntegrationTest.TestSecurityConfig.class)
public abstract class AbstractCardIntegrationTest {

    /**
     * One container for the whole JVM (singleton pattern, same as the backend's base class).
     * {@code @Testcontainers}/{@code @Container} stopped it after each test class while Spring kept
     * the cached context pointing at the old port, so the second class that touched the database
     * timed out. Ryuk removes the container when the JVM exits.
     */
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("card_test")
            .withUsername("card_user")
            .withPassword("card_pass")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

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
            // Keycloak is not running in tests. Every token is rejected except ROLES_TOKEN ones,
            // which decode to a Keycloak-shaped JWT so the real role converter can be exercised.
            return token -> {
                if (!token.startsWith(ROLES_TOKEN)) {
                    throw new BadJwtException("JWT validation disabled in tests");
                }
                List<String> roles = Arrays.stream(token.substring(ROLES_TOKEN.length()).split("\\."))
                        .filter(r -> !r.isEmpty())
                        .toList();
                Instant now = Instant.now();
                return Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("8f6c1a52-2f3e-4c1b-9d7a-5b0e3c4d2a10")
                        .claim("realm_access", Map.of("roles", roles))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .build();
            };
        }
    }

    /**
     * Test bearer token carrying Keycloak realm roles: {@code ROLES_TOKEN + ".ADMIN.TELLER"}.
     * Dots, not commas — the bearer-token resolver accepts base64url characters only.
     */
    public static final String ROLES_TOKEN = "test-roles";
}

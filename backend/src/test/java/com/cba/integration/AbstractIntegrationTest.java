package com.cba.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests.
 * Starts a real PostgreSQL 16 container via Testcontainers.
 * Flyway runs V1 + V2 migrations automatically on startup.
 *
 * Inheriting classes get a fully migrated, real database —
 * no mocks, no H2 in-memory DB with its own quirks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cba_test")
            .withUsername("cba_user")
            .withPassword("cba_pass")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The `test` profile declares the Testcontainers JDBC driver (jdbc:tc:...).
        // We drive the container explicitly via @Container above, so override the
        // driver back to the real PostgreSQL driver — otherwise Flyway/the datasource
        // reject the plain jdbc:postgresql:// URL ("ContainerDatabaseDriver claims to
        // not accept jdbcUrl").
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Do NOT set spring.flyway.url on its own — that makes Flyway open a separate
        // credential-less connection (SCRAM auth failure). Leaving it unset lets Flyway
        // inherit the datasource url+username+password above.
    }
}

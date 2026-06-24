package com.cba.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests.
 * Starts a real PostgreSQL 16 container via Testcontainers.
 * Flyway runs all migrations automatically on startup.
 *
 * Inheriting classes get a fully migrated, real database —
 * no mocks, no H2 in-memory DB with its own quirks.
 *
 * <p><b>Singleton-container pattern.</b> The container is started exactly once,
 * from a static initializer, and shared by every subclass for the lifetime of the
 * JVM (the surefire fork) — it is deliberately NOT managed by the JUnit
 * {@code @Testcontainers} / {@code @Container} extension. With several IT classes
 * ({@code PaymentServiceIT}, {@code CustomerRepositoryIT}, {@code OpenApiSnapshotTest},
 * {@code BackendContextLoadIntegrationTest}), the per-class start/stop that
 * {@code @Testcontainers} performs would spin up and tear down a fresh container for
 * each class and can race on teardown. One container, started here and reclaimed by
 * Ryuk / JVM shutdown, is faster and avoids that lifecycle churn entirely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings("resource") // singleton container is intentionally never closed — reclaimed by Ryuk / JVM shutdown
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cba_test")
            .withUsername("cba_user")
            .withPassword("cba_pass")
            .withReuse(true);
        // Start once for the whole suite. No explicit stop: a reusable/singleton
        // container is reclaimed by Ryuk (or JVM shutdown), not per test class.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The `test` profile declares the Testcontainers JDBC driver (jdbc:tc:...).
        // We drive the singleton container explicitly (see the static initializer
        // above), so override the driver back to the real PostgreSQL driver —
        // otherwise Flyway/the datasource reject the plain jdbc:postgresql:// URL
        // ("ContainerDatabaseDriver claims to not accept jdbcUrl").
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Do NOT set spring.flyway.url on its own — that makes Flyway open a separate
        // credential-less connection (SCRAM auth failure). Leaving it unset lets Flyway
        // inherit the datasource url+username+password above.
    }
}

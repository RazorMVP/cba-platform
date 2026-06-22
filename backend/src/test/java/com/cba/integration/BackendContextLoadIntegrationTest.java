package com.cba.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-boot integration test for the backend monolith.
 *
 * <p>Boots the full Spring context against a real PostgreSQL 16 (Testcontainers),
 * with the {@code test} profile active — which uses {@code ddl-auto=validate}. This
 * makes the test a genuine "would this start in production?" check: it catches
 * duplicate YAML keys, ambiguous request mappings, bean-wiring failures, AND
 * Hibernate entity↔schema drift (every {@code @Entity} is validated against the
 * Flyway-migrated schema). Auth is bypassed via {@code app.auth-bypass=true}, so
 * no Keycloak is needed.
 *
 * <p>Runs under {@code -Pfull-integration} only (needs Docker).
 */
class BackendContextLoadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    @DisplayName("the full application context boots against a real DB with ddl-auto=validate")
    void contextLoads() {
        assertThat(context).isNotNull();
        // A non-trivial bean count confirms the full context (controllers, services,
        // repositories, security, JPA) wired and started — not an empty/partial context.
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(200);
    }
}

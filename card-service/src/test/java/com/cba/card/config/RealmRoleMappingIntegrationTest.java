package com.cba.card.config;

import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keycloak realm roles reach {@code @PreAuthorize} and the URL rules on both JWT chains
 * (card-api and the catch-all), through the real converter over real HTTP.
 */
@DisplayName("Realm roles — mapped on every JWT chain over real HTTP")
class RealmRoleMappingIntegrationTest extends AbstractCardIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    @DisplayName("catch-all chain: ADMIN-only BIN list admits ADMIN, refuses TELLER and a token without roles")
    void publicChain() {
        assertThat(status(HttpMethod.GET, "/api/v1/bins", "ADMIN")).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/api/v1/bins", "TELLER")).isEqualTo(403);
        assertThat(status(HttpMethod.GET, "/api/v1/bins")).isEqualTo(403);
    }

    @Test
    @DisplayName("card-api chain: ADMIN-only API key list admits ADMIN, refuses TELLER")
    void cardApiChain() {
        assertThat(status(HttpMethod.GET, "/card-api/v1/api-keys", "ADMIN")).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/card-api/v1/api-keys", "TELLER")).isEqualTo(403);
    }

    @Test
    @DisplayName("CUSTOMER can't reach a card by id (no ownership check yet); TELLER gets past the role check")
    void customerRefusedOnCardById() {
        String path = "/card-api/v1/cards/" + UUID.randomUUID();
        assertThat(status(HttpMethod.GET, path, "CUSTOMER")).isEqualTo(403);
        assertThat(status(HttpMethod.GET, path, "TELLER")).isEqualTo(404);
    }

    @Test
    @DisplayName("URL rule: the terminal simulator refuses CUSTOMER")
    void simulatorUrlRule() {
        assertThat(status(HttpMethod.POST, "/api/v1/simulate/network/echo", "CUSTOMER")).isEqualTo(403);
    }

    private int status(HttpMethod method, String path, String... roles) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ROLES_TOKEN + (roles.length == 0 ? "" : "." + String.join(".", roles)));
        return rest.exchange(path, method, new HttpEntity<>(headers), String.class).getStatusCode().value();
    }
}

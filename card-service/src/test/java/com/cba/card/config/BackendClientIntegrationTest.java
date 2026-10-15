package com.cba.card.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * card-service → backend over a real socket: one WireMock container plays Keycloak's token
 * endpoint and the backend's {@code GET /api/v1/accounts/{id}/balance}, which REQUIRES
 * {@code Authorization: Bearer it-token} (as the backend's CARD_SERVICE rule does).
 */
@Testcontainers
@DisplayName("Backend client — service token to the backend, over a WireMock container")
class BackendClientIntegrationTest {

    @Container
    static final GenericContainer<?> WIREMOCK =
            new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    static final String TOKEN_PATH = "/realms/cba/protocol/openid-connect/token";
    static final String ACCOUNT = "bbbbbbbb-0000-0000-0000-0000000000c1";
    private static final RestTemplate ADMIN = new RestTemplate();

    private static String baseUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
    }

    @BeforeAll
    static void stubs() {
        register(Map.of("method", "POST", "urlPath", TOKEN_PATH,
                        "headers", Map.of("Authorization", Map.of("equalTo", "Basic "
                                + Base64.getEncoder().encodeToString("cba-card-service:it-secret".getBytes()))),
                        "bodyPatterns", List.of(Map.of("contains", "grant_type=client_credentials"))),
                Map.of("access_token", "it-token", "token_type", "Bearer", "expires_in", 300));
        register(Map.of("method", "GET", "urlPath", "/api/v1/accounts/" + ACCOUNT + "/balance",
                        "headers", Map.of("Authorization", Map.of("equalTo", "Bearer it-token"))),
                Map.of("data", Map.of("accountId", ACCOUNT, "currencyCode", "USD", "availableBalance", 500.0,
                        "debitAllowed", true), "errors", List.of()));
    }

    record Funds(String accountId, String currencyCode, java.math.BigDecimal availableBalance, boolean debitAllowed) {}
    record Envelope<T>(T data) {}

    @Test
    @DisplayName("Bearer token sent, fetched once for several calls; the ApiResponse envelope is read")
    void bearerSent_tokenOnce() {
        long before = tokenRequests();
        RestTemplate backend = RestClientConfig.backendRestTemplate("cba-card-service", "it-secret",
                baseUrl() + TOKEN_PATH);
        for (int i = 0; i < 3; i++) {
            Envelope<Funds> body = backend.exchange(baseUrl() + "/api/v1/accounts/" + ACCOUNT + "/balance",
                    HttpMethod.GET, null, new ParameterizedTypeReference<Envelope<Funds>>() {}).getBody();
            assertThat(body.data().availableBalance()).isEqualByComparingTo("500.0");
            assertThat(body.data().currencyCode()).isEqualTo("USD");
        }
        assertThat(tokenRequests() - before).as("one token, reused").isEqualTo(1);
    }

    @Test
    @DisplayName("a wrong secret: no token → ResourceAccessException (the authorization declines RC 91)")
    void wrongSecret() {
        RestTemplate backend = RestClientConfig.backendRestTemplate("cba-card-service", "wrong",
                baseUrl() + TOKEN_PATH);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> backend.getForObject(
                        baseUrl() + "/api/v1/accounts/" + ACCOUNT + "/balance", String.class))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }

    private static long tokenRequests() {
        Map<?, ?> r = ADMIN.postForObject(baseUrl() + "/__admin/requests/count",
                new HttpEntity<>(Map.of("method", "POST", "urlPath", TOKEN_PATH), json()), Map.class);
        return ((Number) r.get("count")).longValue();
    }

    private static void register(Map<String, Object> request, Map<String, Object> body) {
        ADMIN.postForEntity(baseUrl() + "/__admin/mappings", new HttpEntity<>(Map.of("request", request,
                "response", Map.of("status", 200, "headers", Map.of("Content-Type", "application/json"),
                        "jsonBody", body)), json()), String.class);
    }

    private static HttpHeaders json() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}

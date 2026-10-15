package com.cba.integration;

import com.cba.config.CardServiceConfig;
import com.cba.openbanking.card.CardServiceClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backend → card-service over a real socket, against a WireMock container standing in for
 * card-service. Every card stub REQUIRES {@code Authorization: Bearer it-token}, as
 * card-service's JWT chain does; without it WireMock answers 404 and the fail-safe client
 * returns nothing.
 */
@Testcontainers
@DisplayName("CardServiceClient — service token to card-service, over a WireMock container")
class CardServiceClientIntegrationTest {

    @Container
    static final GenericContainer<?> WIREMOCK =
            new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    static final UUID CUSTOMER = UUID.fromString("30000000-0000-0000-0000-0000000000c1");
    static final UUID CARD = UUID.fromString("50000000-0000-0000-0000-0000000000c1");

    private static final RestTemplate ADMIN = new RestTemplate();

    private static String baseUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
    }

    static final String TOKEN_PATH = "/realms/cba/protocol/openid-connect/token";

    @BeforeAll
    static void registerStubs() {
        // Keycloak's token endpoint: client_credentials with the client authenticated by HTTP Basic.
        Map<String, Object> token = new java.util.LinkedHashMap<>();
        token.put("method", "POST");
        token.put("urlPath", TOKEN_PATH);
        token.put("headers", Map.of("Authorization", Map.of("equalTo", "Basic "
                + Base64.getEncoder().encodeToString("cba-backend-service:it-secret".getBytes()))));
        token.put("bodyPatterns", List.of(Map.of("contains", "grant_type=client_credentials")));
        register(token, Map.of("status", 200, "headers", Map.of("Content-Type", "application/json"),
                "jsonBody", Map.of("access_token", "it-token", "token_type", "Bearer", "expires_in", 300)));

        stubCardGet("/api/v1/cards", Map.of("customerId", Map.of("equalTo", CUSTOMER.toString())),
                Map.of("data", List.of(Map.of("id", CARD.toString(), "cardType", "DEBIT", "status", "ACTIVE",
                        "panPrefix", "50612345", "panSuffix", "1234", "expiryDate", "2912",
                        "customerId", CUSTOMER.toString(), "productName", "Classic Debit"))));
        stubCardGet("/api/v1/cards/" + CARD + "/balance", null,
                Map.of("data", Map.of("availableBalance", 120.5, "cardType", "DEBIT")));
        stubCardGet("/api/v1/cards/" + CARD + "/authorizations", null,
                Map.of("data", List.of(Map.ofEntries(
                        Map.entry("id", UUID.randomUUID().toString()), Map.entry("stan", "000123"),
                        Map.entry("rrn", "R1"), Map.entry("amount", 45.0), Map.entry("currencyCode", "840"),
                        Map.entry("responseCode", "00"), Map.entry("merchantName", "Shop"),
                        Map.entry("merchantId", "M1"), Map.entry("mcc", "5411"), Map.entry("financial", true),
                        Map.entry("createdAt", "2026-10-14T10:00:00+01:00")))));
    }

    private static RestTemplate configured(String secret) {
        return CardServiceConfig.cardServiceRestTemplate(new RestTemplateBuilder(), baseUrl(),
                "cba-backend-service", secret, baseUrl() + TOKEN_PATH);
    }

    @Test
    @DisplayName("with the service credential: cards, balance and authorizations arrive, and one token serves every call")
    void serviceToken_cardDataArrives_tokenFetchedOnce() {
        long tokensBefore = tokenRequests();
        CardServiceClient client = new CardServiceClient(configured("it-secret"));

        assertThat(client.getCardsForCustomer(CUSTOMER)).singleElement()
                .satisfies(c -> {
                    assertThat(c.id()).isEqualTo(CARD);
                    assertThat(c.panSuffix()).isEqualTo("1234");
                });
        assertThat(client.getCardBalance(CARD)).hasValueSatisfying(
                b -> assertThat(b.availableBalance()).isEqualByComparingTo(new BigDecimal("120.5")));
        assertThat(client.getCardAuthorizations(CARD)).singleElement()
                .satisfies(a -> {
                    assertThat(a.financial()).isTrue();
                    assertThat(a.createdAt()).isEqualTo(java.time.Instant.parse("2026-10-14T09:00:00Z"));
                });

        assertThat(tokenRequests() - tokensBefore).as("token fetched once, then reused").isEqualTo(1);
    }

    @Test
    @DisplayName("a wrong client secret: no token, and the client degrades to nothing instead of throwing")
    void wrongSecret_degradesToNothing() {
        CardServiceClient client = new CardServiceClient(configured("wrong-secret"));
        assertThat(client.getCardsForCustomer(CUSTOMER)).isEmpty();
        assertThat(client.getCardBalance(CARD)).isEmpty();
    }

    @Test
    @DisplayName("without a service token (today's wiring) card-service refuses and the client returns nothing")
    void noToken_nothingReturned() {
        RestTemplate plain = new RestTemplateBuilder().rootUri(baseUrl()).build();
        assertThat(new CardServiceClient(plain).getCardsForCustomer(CUSTOMER)).isEmpty();
    }

    // ── WireMock admin helpers ────────────────────────────────────────────────

    private static void stubCardGet(String path, Map<String, Object> query, Map<String, Object> body) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("method", "GET");
        request.put("urlPath", path);
        request.put("headers", Map.of("Authorization", Map.of("equalTo", "Bearer it-token")));
        if (query != null) request.put("queryParameters", query);
        register(request, Map.of("status", 200, "headers", Map.of("Content-Type", "application/json"),
                "jsonBody", body));
    }

    private static long tokenRequests() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<?, ?> r = ADMIN.postForObject(baseUrl() + "/__admin/requests/count",
                new HttpEntity<>(Map.of("method", "POST", "urlPath", TOKEN_PATH), h), Map.class);
        return ((Number) r.get("count")).longValue();
    }

    private static void register(Map<String, Object> request, Map<String, Object> response) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ADMIN.postForEntity(baseUrl() + "/__admin/mappings",
                new HttpEntity<>(Map.of("request", request, "response", response), h), String.class);
    }
}

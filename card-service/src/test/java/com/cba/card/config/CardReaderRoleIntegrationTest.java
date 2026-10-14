package com.cba.card.config;

import com.cba.card.integration.AbstractCardIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CARD_READER is the realm role of the backend's service account (Keycloak client
 * cba-backend-service). It reads what Open Banking needs — cards, balance, authorization
 * history — and nothing else.
 */
@DisplayName("CARD_READER — the backend's service role reads cards and changes nothing")
class CardReaderRoleIntegrationTest extends AbstractCardIntegrationTest {

    static final UUID DEMO_CARD = UUID.fromString("22222222-0001-0001-0001-000000000001");

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("reads: cards by customer, card, balance, authorization history (the backend's 4 calls)")
    void readsAllowed() throws Exception {
        UUID customer = jdbc.queryForObject("SELECT customer_id FROM cards WHERE id = ?", UUID.class, DEMO_CARD);
        jdbc.update("""
                INSERT INTO authorization_log (card_id, stan, rrn, mti, amount, currency_code, response_code,
                                               merchant_id, merchant_name, mcc, is_financial)
                VALUES (?, '900001', 'RDR000000001', '0200', 45.00, '840', '00', 'M-READER', 'Reader Shop', '5411', true)""",
                DEMO_CARD);

        assertThat(get("/api/v1/cards?customerId=" + customer).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/cards/" + DEMO_CARD).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/cards/" + DEMO_CARD + "/balance").getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> history = get("/api/v1/cards/" + DEMO_CARD + "/authorizations");
        assertThat(history.getStatusCode().value()).as(history.getBody()).isEqualTo(200);
        JsonNode row = null;
        for (JsonNode n : json.readTree(history.getBody()).get("data")) {
            if ("RDR000000001".equals(n.path("rrn").asText())) row = n;
        }
        assertThat(row).as("the inserted authorization, in the shape the backend's client parses").isNotNull();
        assertThat(row.get("financial").asBoolean()).isTrue();
        assertThat(row.get("merchantName").asText()).isEqualTo("Reader Shop");
        assertThat(Instant.parse(row.get("createdAt").asText())).isNotNull();
    }

    @Test
    @DisplayName("writes and admin reads are refused: card command, BIN list, API keys")
    void everythingElseRefused() {
        assertThat(exchange(HttpMethod.POST, "/api/v1/cards/" + DEMO_CARD + "?command=block").getStatusCode().value())
                .isEqualTo(403);
        assertThat(get("/api/v1/bins").getStatusCode().value()).isEqualTo(403);
        assertThat(get("/card-api/v1/api-keys").getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ROLES_TOKEN + ".CARD_READER");
        return rest.exchange(path, method, new HttpEntity<>(headers), String.class);
    }
}

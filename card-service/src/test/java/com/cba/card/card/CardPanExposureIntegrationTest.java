package com.cba.card.card;

import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A full PAN or the CVV must never leave card-service in a response (PCI DSS 4.0 §3.4.1, §3.3).
 * {@code Card} is returned as JSON by both card APIs; until backlog 2 PR A it carried {@code panEncrypted}
 * and {@code cvvEncrypted}, which hold the clear values (the columns aren't encrypted yet — PR B).
 */
@DisplayName("Card responses never carry the full PAN or the CVV")
class CardPanExposureIntegrationTest extends AbstractCardIntegrationTest {

    static final UUID DEMO_CARD = UUID.fromString("22222222-0001-0001-0001-000000000001");

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("card reads on /api/v1 and /card-api/v1: masked fields only")
    void cardReadsAreMasked() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT customer_id, pan_encrypted, cvv_encrypted, pan_suffix FROM cards WHERE id = ?", DEMO_CARD);
        String storedPan = (String) row.get("pan_encrypted");
        String storedCvv = (String) row.get("cvv_encrypted");
        UUID customer = (UUID) row.get("customer_id");

        for (String path : new String[] {
                "/api/v1/cards/" + DEMO_CARD,
                "/api/v1/cards?customerId=" + customer,
                "/card-api/v1/cards/" + DEMO_CARD,
                "/card-api/v1/cards?customerId=" + customer }) {
            ResponseEntity<String> resp = get(path);
            assertThat(resp.getStatusCode().value()).as(path + " " + resp.getBody()).isEqualTo(200);
            String body = resp.getBody();
            assertThat(body).as(path).doesNotContain("panEncrypted", "cvvEncrypted", storedPan, storedCvv);
            assertThat(body).as(path + " still shows the masking fields")
                    .contains("\"panSuffix\":\"" + row.get("pan_suffix") + "\"", "\"panPrefix\"");
        }
    }

    @Test
    @DisplayName("the admin GET de-tokenize endpoint is gone — it returned a full PAN")
    void noPublicDetokenize() {
        ResponseEntity<String> resp = get("/api/v1/tokens/detokenize?dpan=9999000000001111");
        // The path now matches only DELETE /api/v1/tokens/{tokenRef}, so a GET is 405 (it was a 500 before the
        // handler for HttpRequestMethodNotSupportedException).
        assertThat(resp.getStatusCode().value()).as(resp.getBody()).isEqualTo(405);
        assertThat(resp.getBody()).contains("METHOD_NOT_ALLOWED").doesNotContain("4111111111111111");

        ResponseEntity<String> unknown = get("/api/v1/no-such-endpoint");
        assertThat(unknown.getStatusCode().value()).as(unknown.getBody()).isEqualTo(404);
        assertThat(unknown.getBody()).contains("NOT_FOUND");
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ROLES_TOKEN + ".ADMIN.TELLER");
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}

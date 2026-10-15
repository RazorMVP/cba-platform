package com.cba.card.config;

import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /api/v1/internal/**} — fep-service's endpoints, PAN de-tokenisation among them —
 * accept only a token carrying FEP_SERVICE (the realm role of Keycloak client
 * cba-fep-service). They were permitAll on the strength of network isolation that the k8s
 * ingress never provided.
 */
@DisplayName("Internal chain — fep-service only (FEP_SERVICE), over real HTTP")
class InternalChainIntegrationTest extends AbstractCardIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    static final Map<String, Object> DETOKENIZE = Map.of("dpan", "9999990000000001");
    static final Map<String, Object> ADVISE = Map.of("pan", "4111110000000001", "amount", 10, "stan", "700001");
    static final Map<String, Object> REVERSE = Map.of("pan", "4111110000000001", "amount", 10, "stan", "700002",
            "originalDataElements", "0100700002");
    static final Map<String, Object> AUTHORIZE = Map.ofEntries(
            Map.entry("pan", "4111110000000001"), Map.entry("processingCode", "000000"), Map.entry("amount", 1000),
            Map.entry("currencyCode", "840"), Map.entry("stan", "700003"), Map.entry("terminalId", "TERM0001"),
            Map.entry("merchantId", "MERCHANT0000001"), Map.entry("merchantName", "IT Shop"), Map.entry("mcc", "5411"),
            Map.entry("posEntryMode", "051"), Map.entry("rrn", "612345700003"), Map.entry("scheme", "VISA"),
            Map.entry("pinVerified", true), Map.entry("arqcValid", true), Map.entry("isFinancial", false));

    @Test
    @DisplayName("no token: 401 on every internal endpoint (de-tokenisation included)")
    void noToken_refused() {
        assertThat(post("/api/v1/internal/detokenize", DETOKENIZE, null)).isEqualTo(401);
        assertThat(post("/api/v1/internal/authorize", AUTHORIZE, null)).isEqualTo(401);
        assertThat(post("/api/v1/internal/advise", ADVISE, null)).isEqualTo(401);
        assertThat(post("/api/v1/internal/reverse", REVERSE, null)).isEqualTo(401);
    }

    @Test
    @DisplayName("people's and other services' roles: 403 (ADMIN, TELLER, CUSTOMER, CARD_READER)")
    void otherRoles_forbidden() {
        for (String role : new String[]{"ADMIN", "TELLER", "CUSTOMER", "CARD_READER"}) {
            assertThat(post("/api/v1/internal/detokenize", DETOKENIZE, role)).as(role).isEqualTo(403);
            assertThat(post("/api/v1/internal/authorize", AUTHORIZE, role)).as(role).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("FEP_SERVICE reaches every handler")
    void fepService_allowed() {
        assertThat(post("/api/v1/internal/detokenize", DETOKENIZE, "FEP_SERVICE")).isEqualTo(200);
        assertThat(post("/api/v1/internal/authorize", AUTHORIZE, "FEP_SERVICE")).isEqualTo(200);
        assertThat(post("/api/v1/internal/advise", ADVISE, "FEP_SERVICE")).isEqualTo(200);
        assertThat(post("/api/v1/internal/reverse", REVERSE, "FEP_SERVICE")).isEqualTo(200);
    }

    @Test
    @DisplayName("unknown card: a clean RC 14 decline (was a 500), and the attempt stays in the authorization log")
    void unknownCard_declined14_andLogged() {
        Map<String, Object> body = new java.util.HashMap<>(AUTHORIZE);
        body.put("stan", "700099");
        ResponseEntity<Map> r = rest.exchange("/api/v1/internal/authorize", HttpMethod.POST,
                new HttpEntity<>(body, headers("FEP_SERVICE")), Map.class);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).containsEntry("responseCode", "14");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM authorization_log WHERE stan = '700099' AND response_code = '14'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the unused internal card-history endpoint is gone (history stays on /api/v1/cards/{id}/authorizations)")
    void internalHistoryRemoved() {
        HttpHeaders h = headers("FEP_SERVICE");
        int status = rest.exchange("/api/v1/internal/cards/" + UUID.randomUUID() + "/authorizations",
                HttpMethod.GET, new HttpEntity<>(h), String.class).getStatusCode().value();
        assertThat(status).isIn(404, 500); // no handler: card-service's catch-all maps NoResourceFound to 500
        assertThat(status).isNotEqualTo(200);
    }

    @Test
    @DisplayName("k8s probes: /actuator/health/readiness and /liveness answer without a token")
    void probesOpen() {
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(rest.getForEntity("/actuator/health/liveness", String.class).getStatusCode().value()).isEqualTo(200);
    }

    private int post(String path, Map<String, Object> body, String role) {
        ResponseEntity<String> r = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(role)), String.class);
        return r.getStatusCode().value();
    }

    private static HttpHeaders headers(String role) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (role != null) h.setBearerAuth(ROLES_TOKEN + "." + role);
        return h;
    }
}

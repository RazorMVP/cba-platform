package com.cba.card.config;

import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the CVE-2026-41707 compensating control runs BEFORE every card-service security chain:
 * only DPoPRejectionFilter produces DPOP_NOT_SUPPORTED, so Spring's DPoP filter never sees it.
 */
@DisplayName("DPoP rejection — wired ahead of every security chain over real HTTP")
class DPoPRejectionIntegrationTest extends AbstractCardIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    @DisplayName("card-api and internal-API chains both reject Authorization: DPoP with our 401")
    void dpopRejectedOnEveryChain() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "DPoP eyJhbGciOiJSUzI1NiJ9.e30.sig");
        headers.set("DPoP", "eyJ0eXAiOiJkcG9wK2p3dCJ9.e30.sig");

        for (String path : new String[] { "/card-api/v1/cards", "/api/v1/cards/products" }) {
            ResponseEntity<String> res = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            assertThat(res.getStatusCode().value()).as(path).isEqualTo(401);
            assertThat(res.getBody()).as(path).contains("DPOP_NOT_SUPPORTED");
        }
    }
}

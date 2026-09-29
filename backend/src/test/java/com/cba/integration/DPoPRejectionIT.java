package com.cba.integration;

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
 * Proves the CVE-2026-41707 compensating control is wired in the real servlet chain and runs
 * BEFORE Spring Security: only DPoPRejectionFilter produces DPOP_NOT_SUPPORTED, so seeing it
 * means Spring's DPoP filter (and its vulnerable replay cache) never received the request.
 */
@DisplayName("DPoP rejection — wired ahead of Spring Security over real HTTP")
class DPoPRejectionIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    @DisplayName("Authorization: DPoP → 401 DPOP_NOT_SUPPORTED from our filter")
    void dpopRejectedBeforeSecurity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "DPoP eyJhbGciOiJSUzI1NiJ9.e30.sig");
        headers.set("DPoP", "eyJ0eXAiOiJkcG9wK2p3dCJ9.e30.sig");

        ResponseEntity<String> res = rest.exchange("/api/v1/glaccounts", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(res.getBody()).contains("DPOP_NOT_SUPPORTED");
    }

    @Test
    @DisplayName("the same request without DPoP still reaches the controller")
    void otherRequestsUnaffected() {
        ResponseEntity<String> res = rest.getForEntity("/api/v1/glaccounts", String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }
}

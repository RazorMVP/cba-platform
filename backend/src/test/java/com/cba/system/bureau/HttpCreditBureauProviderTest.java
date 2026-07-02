package com.cba.system.bureau;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("HttpCreditBureauProvider — real bureau adapter")
class HttpCreditBureauProviderTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    private CreditCheckRequest req() {
        return new CreditCheckRequest(UUID.randomUUID(), "NID-1", "Jane Doe", "KE");
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("2xx with a score → HIT, band derived, Bearer auth + JSON body sent")
    void pull_hit() {
        HttpCreditBureauProvider provider = new HttpCreditBureauProvider(restTemplate, "https://bureau.test/pull", "key123");
        server.expect(requestTo("https://bureau.test/pull"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer key123"))
              .andExpect(jsonPath("$.nationalId").value("NID-1"))
              .andRespond(withSuccess("{\"score\":712,\"reference\":\"TU-42\"}", MediaType.APPLICATION_JSON));

        CreditReport r = provider.pull(req());

        assertThat(r.status()).isEqualTo(CreditReport.Status.HIT);
        assertThat(r.score()).isEqualTo(712);
        assertThat(r.band()).isEqualTo("GOOD");
        assertThat(r.reference()).isEqualTo("TU-42");
        server.verify();
    }

    @Test
    @DisplayName("2xx with no score → NO_HIT (thin file)")
    void pull_noHit() {
        HttpCreditBureauProvider provider = new HttpCreditBureauProvider(restTemplate, "https://bureau.test/pull", "");
        server.expect(requestTo("https://bureau.test/pull"))
              .andRespond(withSuccess("{\"found\":false}", MediaType.APPLICATION_JSON));

        assertThat(provider.pull(req()).status()).isEqualTo(CreditReport.Status.NO_HIT);
    }

    @Test
    @DisplayName("server error → UNAVAILABLE (never throws)")
    void pull_serverError() {
        HttpCreditBureauProvider provider = new HttpCreditBureauProvider(restTemplate, "https://bureau.test/pull", "k");
        server.expect(requestTo("https://bureau.test/pull")).andRespond(withServerError());

        assertThat(provider.pull(req()).status()).isEqualTo(CreditReport.Status.UNAVAILABLE);
    }

    @Test
    @DisplayName("blank URL → UNAVAILABLE, no HTTP call made")
    void pull_noUrl() {
        HttpCreditBureauProvider provider = new HttpCreditBureauProvider(restTemplate, "", "k");

        CreditReport r = provider.pull(req());

        assertThat(r.status()).isEqualTo(CreditReport.Status.UNAVAILABLE);
        server.verify(); // zero expectations → nothing sent
    }

    @Test
    @DisplayName("providerId is HTTP")
    void providerId() {
        assertThat(new HttpCreditBureauProvider(restTemplate, "u", "k").providerId()).isEqualTo("HTTP");
    }
}

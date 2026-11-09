package com.cba.payment.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("HttpExternalPaymentGateway — real gateway adapter")
class HttpExternalPaymentGatewayTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    private ExternalPaymentInstruction instruction() {
        return new ExternalPaymentInstruction("SEPA", new BigDecimal("250.00"), "EUR",
                "Jane Doe", "DE89370400440532013000", "COBADEFF", "Commerzbank", "DEU", "SHA", "EXT-XYZ");
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("2xx with a networkReference → accepted; Bearer + JSON body sent")
    void submit_accepted() {
        HttpExternalPaymentGateway gw = new HttpExternalPaymentGateway(restTemplate, "https://psp.test/pay", "key123");
        server.expect(requestTo("https://psp.test/pay"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer key123"))
              .andExpect(jsonPath("$.network").value("SEPA"))
              .andExpect(jsonPath("$.beneficiaryBic").value("COBADEFF"))
              .andRespond(withSuccess("{\"uetr\":\"abc-123-uetr\"}", MediaType.APPLICATION_JSON));

        GatewayResult r = gw.submit(instruction());

        assertThat(r.accepted()).isTrue();
        assertThat(r.networkReference()).isEqualTo("abc-123-uetr");
        server.verify();
    }

    @Test
    @DisplayName("5xx → UNKNOWN, not rejected: the payment may have gone out (never throws)")
    void submit_serverError() {
        HttpExternalPaymentGateway gw = new HttpExternalPaymentGateway(restTemplate, "https://psp.test/pay", "k");
        server.expect(requestTo("https://psp.test/pay")).andRespond(withServerError());

        GatewayResult r = gw.submit(instruction());

        assertThat(r.status()).isEqualTo(GatewayResult.Status.UNKNOWN);
        assertThat(r.errorCode()).isEqualTo("HTTP_500");
    }

    @Test
    @DisplayName("4xx → REJECTED: a definite refusal, so the debit rolls back")
    void submit_clientError() {
        HttpExternalPaymentGateway gw = new HttpExternalPaymentGateway(restTemplate, "https://psp.test/pay", "k");
        server.expect(requestTo("https://psp.test/pay")).andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY));

        GatewayResult r = gw.submit(instruction());

        assertThat(r.status()).isEqualTo(GatewayResult.Status.REJECTED);
        assertThat(r.errorCode()).isEqualTo("HTTP_422");
    }

    @Test
    @DisplayName("transport error (timeout, reset) → UNKNOWN")
    void submit_transportError() {
        HttpExternalPaymentGateway gw = new HttpExternalPaymentGateway(restTemplate, "https://psp.test/pay", "k");
        server.expect(requestTo("https://psp.test/pay")).andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        GatewayResult r = gw.submit(instruction());

        assertThat(r.status()).isEqualTo(GatewayResult.Status.UNKNOWN);
        assertThat(r.errorCode()).isEqualTo("TRANSPORT_ERROR");
    }

    @Test
    @DisplayName("an accepted submit is not settled: settlement comes by callback")
    void submit_acceptedNotSettled() {
        HttpExternalPaymentGateway gw = new HttpExternalPaymentGateway(restTemplate, "https://psp.test/pay", "k");
        server.expect(requestTo("https://psp.test/pay")).andRespond(withSuccess("{\"id\":\"T1\"}", MediaType.APPLICATION_JSON));

        assertThat(gw.submit(instruction()).settled()).isFalse();
    }

    @Test
    @DisplayName("blank URL → rejected NO_GATEWAY_URL, no HTTP call")
    void submit_noUrl() {
        HttpExternalPaymentGateway gw = new HttpExternalPaymentGateway(restTemplate, "", "k");

        GatewayResult r = gw.submit(instruction());

        assertThat(r.accepted()).isFalse();
        assertThat(r.errorCode()).isEqualTo("NO_GATEWAY_URL");
        server.verify();
    }

    @Test
    @DisplayName("gatewayId is HTTP")
    void gatewayId() {
        assertThat(new HttpExternalPaymentGateway(restTemplate, "u", "k").gatewayId()).isEqualTo("HTTP");
    }
}

package com.cba.notification.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("HttpSmsProvider — real HTTP gateway adapter")
class HttpSmsProviderTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("2xx with a message id → accepted, id extracted, Bearer auth + JSON body sent")
    void send_success() {
        HttpSmsProvider provider = new HttpSmsProvider(restTemplate, "https://gw.test/send", "key123", "CBA");
        server.expect(requestTo("https://gw.test/send"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer key123"))
              .andExpect(jsonPath("$.to").value("+254700000000"))
              .andExpect(jsonPath("$.from").value("CBA"))
              .andExpect(jsonPath("$.message").value("hi"))
              .andRespond(withSuccess("{\"messageId\":\"AT-999\"}", MediaType.APPLICATION_JSON));

        SmsProvider.SmsResult r = provider.send("+254700000000", "hi");

        assertThat(r.accepted()).isTrue();
        assertThat(r.providerMessageId()).isEqualTo("AT-999");
        server.verify();
    }

    @Test
    @DisplayName("2xx with no recognisable id → accepted with synthetic marker")
    void send_success_noId() {
        HttpSmsProvider provider = new HttpSmsProvider(restTemplate, "https://gw.test/send", "", "CBA");
        server.expect(requestTo("https://gw.test/send"))
              .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        SmsProvider.SmsResult r = provider.send("+254700000000", "hi");

        assertThat(r.accepted()).isTrue();
        assertThat(r.providerMessageId()).isEqualTo("http-accepted");
    }

    @Test
    @DisplayName("server error → rejected (never throws), transport error code")
    void send_serverError() {
        HttpSmsProvider provider = new HttpSmsProvider(restTemplate, "https://gw.test/send", "k", "CBA");
        server.expect(requestTo("https://gw.test/send"))
              .andRespond(withServerError());

        SmsProvider.SmsResult r = provider.send("+254700000000", "hi");

        assertThat(r.accepted()).isFalse();
        assertThat(r.errorCode()).isNotNull();
    }

    @Test
    @DisplayName("blank gateway URL → rejected NO_GATEWAY_URL, no HTTP call made")
    void send_noUrl() {
        HttpSmsProvider provider = new HttpSmsProvider(restTemplate, "", "k", "CBA");

        SmsProvider.SmsResult r = provider.send("+254700000000", "hi");

        assertThat(r.accepted()).isFalse();
        assertThat(r.errorCode()).isEqualTo("NO_GATEWAY_URL");
        server.verify(); // zero expectations → confirms nothing was sent
    }

    @Test
    @DisplayName("providerId is HTTP")
    void providerId() {
        assertThat(new HttpSmsProvider(restTemplate, "u", "k", "CBA").providerId()).isEqualTo("HTTP");
    }
}

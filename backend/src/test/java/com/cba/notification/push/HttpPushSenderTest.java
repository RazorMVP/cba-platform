package com.cba.notification.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("HttpPushSender — real push relay adapter")
class HttpPushSenderTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("2xx → accepted with id; Bearer + JSON body sent")
    void send_accepted() {
        HttpPushSender sender = new HttpPushSender(restTemplate, "https://push.test/send", "key123");
        server.expect(requestTo("https://push.test/send"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer key123"))
              .andExpect(jsonPath("$.token").value("tok-1"))
              .andExpect(jsonPath("$.title").value("Hello"))
              .andRespond(withSuccess("{\"messageId\":\"projects/x/messages/1\"}", MediaType.APPLICATION_JSON));

        PushSender.PushResult r = sender.send("tok-1", "Hello", "World", Map.of("a", "b"));

        assertThat(r.accepted()).isTrue();
        assertThat(r.messageId()).isEqualTo("projects/x/messages/1");
        server.verify();
    }

    @Test
    @DisplayName("404 → token marked invalid (for deactivation)")
    void send_notFound_tokenInvalid() {
        HttpPushSender sender = new HttpPushSender(restTemplate, "https://push.test/send", "k");
        server.expect(requestTo("https://push.test/send"))
              .andRespond(withStatus(HttpStatus.NOT_FOUND));

        PushSender.PushResult r = sender.send("dead-token", "t", "b", null);

        assertThat(r.accepted()).isFalse();
        assertThat(r.tokenInvalid()).isTrue();
    }

    @Test
    @DisplayName("500 → rejected, token not marked invalid")
    void send_serverError_rejected() {
        HttpPushSender sender = new HttpPushSender(restTemplate, "https://push.test/send", "k");
        server.expect(requestTo("https://push.test/send"))
              .andRespond(withServerError());

        PushSender.PushResult r = sender.send("tok", "t", "b", null);

        assertThat(r.accepted()).isFalse();
        assertThat(r.tokenInvalid()).isFalse();
    }

    @Test
    @DisplayName("blank URL → rejected, no HTTP call")
    void send_noUrl() {
        HttpPushSender sender = new HttpPushSender(restTemplate, "", "k");

        PushSender.PushResult r = sender.send("tok", "t", "b", null);

        assertThat(r.accepted()).isFalse();
        assertThat(r.errorCode()).isEqualTo("NO_PUSH_URL");
        server.verify();
    }

    @Test
    @DisplayName("providerId is HTTP")
    void providerId() {
        assertThat(new HttpPushSender(restTemplate, "u", "k").providerId()).isEqualTo("HTTP");
    }
}

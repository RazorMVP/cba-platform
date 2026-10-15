package com.cba.fep.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("CardServiceTokenProvider — client_credentials token for card-service")
class CardServiceTokenProviderTest {

    static final String TOKEN_URI = "http://keycloak.test/realms/cba/protocol/openid-connect/token";

    RestTemplate http;
    MockRestServiceServer keycloak;
    MutableClock clock;

    @BeforeEach
    void setUp() {
        http = new RestTemplate();
        keycloak = MockRestServiceServer.bindTo(http).build();
        clock = new MutableClock(Instant.parse("2026-10-15T10:00:00Z"));
    }

    private CardServiceTokenProvider provider() {
        return new CardServiceTokenProvider("cba-fep-service", "s3cret", TOKEN_URI, http, clock);
    }

    private static String token(String value, int expiresIn) {
        return "{\"access_token\":\"" + value + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn + "}";
    }

    @Test
    @DisplayName("client_credentials with HTTP Basic client auth; one token reused until shortly before expiry")
    void fetchesOnce_thenReuses() {
        keycloak.expect(once(), requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("cba-fep-service:s3cret".getBytes(StandardCharsets.UTF_8))))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "client_credentials")))
                .andRespond(withSuccess(token("t1", 300), MediaType.APPLICATION_JSON));

        CardServiceTokenProvider p = provider();
        assertThat(p.token()).isEqualTo("t1");
        clock.advanceSeconds(200);
        assertThat(p.token()).isEqualTo("t1");
        keycloak.verify();
    }

    @Test
    @DisplayName("within 30 s of expiry a new token is fetched")
    void refreshesBeforeExpiry() {
        keycloak.expect(once(), requestTo(TOKEN_URI)).andRespond(withSuccess(token("t1", 300), MediaType.APPLICATION_JSON));
        keycloak.expect(once(), requestTo(TOKEN_URI)).andRespond(withSuccess(token("t2", 300), MediaType.APPLICATION_JSON));

        CardServiceTokenProvider p = provider();
        assertThat(p.token()).isEqualTo("t1");
        clock.advanceSeconds(271); // 300 − 30 = 270 s of use
        assertThat(p.token()).isEqualTo("t2");
        keycloak.verify();
    }

    @Test
    @DisplayName("a refused client (wrong secret) or a response without access_token: ResourceAccessException")
    void failures_areRestClientExceptions() {
        keycloak.expect(once(), requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_client\"}"));
        assertThatThrownBy(() -> provider().token()).isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("No card-service token");

        keycloak.reset();
        keycloak.expect(once(), requestTo(TOKEN_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider().token()).isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("no access_token");
    }

    /** A clock the test moves forward. */
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advanceSeconds(long s) { now = now.plusSeconds(s); }
        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }
}

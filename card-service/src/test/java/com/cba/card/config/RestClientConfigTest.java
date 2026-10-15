package com.cba.card.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RestClientConfig — the backend client carries the token, the settlement client never does")
class RestClientConfigTest {

    private static final String TOKEN_URI = "http://keycloak.invalid/realms/cba/protocol/openid-connect/token";

    @Test
    @DisplayName("with a client secret, backendRestTemplate carries the token interceptor")
    void secret_addsTokenInterceptor() {
        assertThat(RestClientConfig.backendRestTemplate("cba-card-service", "s3cret", TOKEN_URI).getInterceptors())
                .hasSize(1);
    }

    @Test
    @DisplayName("without a client secret, no interceptor (dev with backend bypass)")
    void noSecret_noInterceptor() {
        assertThat(RestClientConfig.backendRestTemplate("cba-card-service", "", TOKEN_URI).getInterceptors()).isEmpty();
    }

    @Test
    @DisplayName("settlementRestTemplate (external clearinghouses) never carries card-service's token")
    void settlementClient_noToken() {
        assertThat(new RestClientConfig().settlementRestTemplate().getInterceptors()).isEmpty();
    }
}

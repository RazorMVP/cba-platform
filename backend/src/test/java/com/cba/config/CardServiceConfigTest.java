package com.cba.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CardServiceConfig — service token wiring")
class CardServiceConfigTest {

    private static final String TOKEN_URI = "http://keycloak.invalid/realms/cba/protocol/openid-connect/token";

    @Test
    @DisplayName("with a client secret, the card-service RestTemplate carries the token interceptor")
    void secret_addsTokenInterceptor() {
        var rest = CardServiceConfig.cardServiceRestTemplate(new RestTemplateBuilder(), "http://card-service:8081",
                "cba-backend-service", "s3cret", TOKEN_URI);
        assertThat(rest.getInterceptors()).hasSize(1);
    }

    @Test
    @DisplayName("without a client secret, no interceptor: calls go unauthenticated (dev with card-service bypass)")
    void noSecret_noInterceptor() {
        assertThat(CardServiceConfig.cardServiceRestTemplate(new RestTemplateBuilder(), "http://card-service:8081",
                "cba-backend-service", "", TOKEN_URI).getInterceptors()).isEmpty();
        assertThat(CardServiceConfig.cardServiceRestTemplate(new RestTemplateBuilder(), "http://card-service:8081",
                "cba-backend-service", null, TOKEN_URI).getInterceptors()).isEmpty();
    }
}

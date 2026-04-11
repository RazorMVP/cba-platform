package com.cba.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP client configuration for calling card-service (:8081).
 *
 * <p>The RestTemplate is named {@code cardServiceRestTemplate} to distinguish it from
 * any other RestTemplate beans in the context. All card-service calls use this bean.
 */
@Configuration
public class CardServiceConfig {

    @Value("${card.service.base-url:http://localhost:8081}")
    private String cardServiceBaseUrl;

    @Bean("cardServiceRestTemplate")
    public RestTemplate cardServiceRestTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri(cardServiceBaseUrl)
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Exposed for injection into the card client. */
    public String getCardServiceBaseUrl() {
        return cardServiceBaseUrl;
    }
}

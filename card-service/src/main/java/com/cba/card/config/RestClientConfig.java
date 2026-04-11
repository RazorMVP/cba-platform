package com.cba.card.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Value("${backend.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    /**
     * RestTemplate for calls to the monolith backend (balance checks, account lookups).
     * In production this would use mTLS or a service mesh sidecar.
     */
    @Bean
    public RestTemplate backendRestTemplate() {
        return new RestTemplate();
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }
}

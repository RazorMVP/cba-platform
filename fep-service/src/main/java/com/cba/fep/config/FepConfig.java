package com.cba.fep.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;

/**
 * FEP Spring configuration.
 *
 * <p>Provides shared infrastructure beans used across the FEP:
 * <ul>
 *   <li>{@link RestTemplate} — synchronous HTTP client for card-service calls, with
 *       connect/read timeouts and, when a client secret is configured, card-service's
 *       service token ({@link CardServiceTokenProvider}; Keycloak client cba-fep-service,
 *       realm role FEP_SERVICE). card-service refuses its internal endpoints without it.</li>
 * </ul>
 *
 * <p>{@link EnableScheduling} activates the BIN cache refresh scheduler
 * defined in {@link com.cba.fep.scheme.SchemeAdapterFactory}.
 * The refresh runs every 5 minutes to pick up new BIN registrations
 * without requiring a restart.
 */
@Slf4j
@Configuration
@EnableScheduling
public class FepConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${fep.card-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${fep.card-service.read-timeout-ms:5000}") int readTimeoutMs,
            @Value("${fep.card-service.auth.client-id:cba-fep-service}") String clientId,
            @Value("${fep.card-service.auth.client-secret:}") String clientSecret,
            @Value("${fep.card-service.auth.token-uri:http://localhost:8180/realms/cba/protocol/openid-connect/token}")
            String tokenUri) {
        return cardServiceRestTemplate(connectTimeoutMs, readTimeoutMs, clientId, clientSecret, tokenUri, Clock.systemUTC());
    }

    /** The bean's wiring with explicit settings (tests). */
    static RestTemplate cardServiceRestTemplate(int connectTimeoutMs, int readTimeoutMs, String clientId,
                                                String clientSecret, String tokenUri, Clock clock) {
        RestTemplate rest = new RestTemplate(timeouts(connectTimeoutMs, readTimeoutMs));
        if (clientSecret == null || clientSecret.isBlank()) {
            log.warn("fep.card-service.auth.client-secret is not set: calls to card-service go without a token, "
                    + "and a card-service that checks tokens refuses them (authorizations decline with RC 96)");
            return rest;
        }
        CardServiceTokenProvider tokens = new CardServiceTokenProvider(clientId, clientSecret, tokenUri,
                new RestTemplate(timeouts(connectTimeoutMs, readTimeoutMs)), clock);
        rest.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setBearerAuth(tokens.token());
            return execution.execute(request, body);
        });
        return rest;
    }

    private static SimpleClientHttpRequestFactory timeouts(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return factory;
    }
}

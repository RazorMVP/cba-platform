package com.cba.card.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP clients for card-service's outbound calls. Two, deliberately separate:
 * <ul>
 *   <li>{@code backendRestTemplate} — the monolith backend only (debit-card balance checks).
 *       It carries card-service's service token: the {@code client_credentials} grant for Keycloak
 *       client {@code cba-card-service}, whose service account holds only realm role CARD_SERVICE
 *       (the backend admits it on {@code GET /api/v1/accounts/{id}/balance} alone). Short timeouts:
 *       it runs inside every card authorization, within fep-service's 5 s budget.</li>
 *   <li>{@code settlementRestTemplate} — external clearinghouses (PAPSS HTTPS settlement). Never
 *       the token: an internal credential must not leave for a third party.</li>
 * </ul>
 * With no client secret configured (local runs where the backend bypasses auth, tests) backend
 * calls go without a token, and a backend that checks tokens refuses them.
 */
@Slf4j
@Configuration
public class RestClientConfig {

    static final String REGISTRATION_ID = "backend";
    static final Duration BACKEND_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration BACKEND_READ_TIMEOUT = Duration.ofSeconds(3);

    /** The principal the shared token is cached under: card-service itself, never a cardholder. */
    private static final Authentication CARD_SERVICE =
            UsernamePasswordAuthenticationToken.unauthenticated("cba-card-service", null);

    @Value("${backend.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    @Value("${backend.auth.client-id:cba-card-service}")
    private String clientId;

    @Value("${backend.auth.client-secret:}")
    private String clientSecret;

    @Value("${backend.auth.token-uri:http://localhost:8180/realms/cba/protocol/openid-connect/token}")
    private String tokenUri;

    @Bean
    public RestTemplate backendRestTemplate() {
        return backendRestTemplate(clientId, clientSecret, tokenUri);
    }

    /** External settlement (clearinghouse HTTPS). No token; generous timeouts for file uploads. */
    @Bean
    public RestTemplate settlementRestTemplate() {
        return new RestTemplate(timeouts(Duration.ofSeconds(10), Duration.ofSeconds(60)));
    }

    /** The backend client's wiring with explicit settings (tests). */
    static RestTemplate backendRestTemplate(String clientId, String clientSecret, String tokenUri) {
        RestTemplate rest = new RestTemplate(timeouts(BACKEND_CONNECT_TIMEOUT, BACKEND_READ_TIMEOUT));
        if (clientSecret == null || clientSecret.isBlank()) {
            log.warn("backend.auth.client-secret is not set: calls to the backend go without a token, and a backend "
                    + "that checks tokens refuses them (debit-card authorizations decline with RC 91)");
            return rest;
        }
        rest.getInterceptors().add(serviceTokenInterceptor(authorizedClientManager(clientId, clientSecret, tokenUri)));
        return rest;
    }

    /**
     * Adds the service token. A token that can't be obtained (Keycloak down, wrong secret)
     * surfaces as a {@link ResourceAccessException}, which the authorization path already treats
     * as "issuer unavailable" (RC 91) instead of failing the transaction.
     */
    static ClientHttpRequestInterceptor serviceTokenInterceptor(OAuth2AuthorizedClientManager manager) {
        OAuth2ClientHttpRequestInterceptor oauth = new OAuth2ClientHttpRequestInterceptor(manager);
        // RestTemplate has no per-request attributes, so both must be fixed (the defaults read them and find nothing).
        oauth.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        oauth.setPrincipalResolver(request -> CARD_SERVICE);
        return (request, body, execution) -> {
            try {
                return oauth.intercept(request, body, execution);
            } catch (OAuth2AuthorizationException e) {
                throw new ResourceAccessException("No backend token: " + e.getError().getErrorCode());
            }
        };
    }

    static OAuth2AuthorizedClientManager authorizedClientManager(String clientId, String clientSecret, String tokenUri) {
        var registrations = new InMemoryClientRegistrationRepository(ClientRegistration
                .withRegistrationId(REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(tokenUri)
                .build());

        // The documented default RestClient for token requests, plus timeouts.
        var tokenClient = new RestClientClientCredentialsTokenResponseClient();
        tokenClient.setRestClient(RestClient.builder()
                .requestFactory(timeouts(BACKEND_CONNECT_TIMEOUT, BACKEND_READ_TIMEOUT))
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());

        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(c -> c.accessTokenResponseClient(tokenClient))
                .build());
        return manager;
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration connect, Duration read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }
}

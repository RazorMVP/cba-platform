package com.cba.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
 * HTTP client configuration for calling card-service (:8081).
 *
 * <p>The RestTemplate is named {@code cardServiceRestTemplate} to distinguish it from
 * any other RestTemplate beans in the context. All card-service calls use this bean.
 *
 * <p>card-service only accepts Keycloak JWTs, so each call carries a service token: the
 * {@code client_credentials} grant for the confidential client {@code cba-backend-service},
 * whose service account holds only the realm role {@code CARD_READER}. One token is shared
 * by the whole application and fetched again shortly before it expires. With no client
 * secret configured (local runs where card-service bypasses auth, tests) calls go
 * unauthenticated, and a real card-service refuses them.
 */
@Slf4j
@Configuration
public class CardServiceConfig {

    static final String REGISTRATION_ID = "card-service";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    /** The principal the shared token is cached under: the backend itself, never the customer. */
    private static final Authentication BACKEND = UsernamePasswordAuthenticationToken.unauthenticated("cba-backend", null);

    @Value("${card.service.base-url:http://localhost:8081}")
    private String cardServiceBaseUrl;

    @Value("${card.service.auth.client-id:cba-backend-service}")
    private String clientId;

    @Value("${card.service.auth.client-secret:}")
    private String clientSecret;

    @Value("${card.service.auth.token-uri:http://localhost:8180/realms/cba/protocol/openid-connect/token}")
    private String tokenUri;

    @Bean("cardServiceRestTemplate")
    public RestTemplate cardServiceRestTemplate(RestTemplateBuilder builder) {
        return cardServiceRestTemplate(builder, cardServiceBaseUrl, clientId, clientSecret, tokenUri);
    }

    /** The bean's wiring, callable with explicit settings (integration tests). */
    public static RestTemplate cardServiceRestTemplate(RestTemplateBuilder builder, String baseUrl,
                                                       String clientId, String clientSecret, String tokenUri) {
        RestTemplateBuilder b = builder
                .rootUri(baseUrl)
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT);
        if (clientSecret == null || clientSecret.isBlank()) {
            log.warn("card.service.auth.client-secret is not set: calls to card-service at {} go without a token "
                    + "and a card-service that checks tokens will refuse them", baseUrl);
            return b.build();
        }
        return b.additionalInterceptors(serviceTokenInterceptor(authorizedClientManager(clientId, clientSecret, tokenUri)))
                .build();
    }

    /**
     * Adds the service token. A token that can't be obtained (Keycloak down, wrong secret)
     * surfaces as a {@link ResourceAccessException}, a {@code RestClientException}, so callers
     * treat it like card-service being unreachable instead of failing the whole request.
     */
    static ClientHttpRequestInterceptor serviceTokenInterceptor(OAuth2AuthorizedClientManager manager) {
        OAuth2ClientHttpRequestInterceptor oauth = new OAuth2ClientHttpRequestInterceptor(manager);
        // RestTemplate has no per-request attributes, so both must be fixed (the defaults read them and find nothing).
        oauth.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        oauth.setPrincipalResolver(request -> BACKEND);
        return (request, body, execution) -> {
            try {
                return oauth.intercept(request, body, execution);
            } catch (OAuth2AuthorizationException e) {
                throw new ResourceAccessException("No card-service token: " + e.getError().getErrorCode());
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

        // The documented default RestClient for token requests, plus timeouts so a hung
        // Keycloak can't hold a request thread.
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        var tokenClient = new RestClientClientCredentialsTokenResponseClient();
        tokenClient.setRestClient(RestClient.builder()
                .requestFactory(factory)
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

    /** Exposed for injection into the card client. */
    public String getCardServiceBaseUrl() {
        return cardServiceBaseUrl;
    }
}

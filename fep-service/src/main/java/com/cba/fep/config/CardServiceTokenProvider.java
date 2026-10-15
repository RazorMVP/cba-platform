package com.cba.fep.config;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The service token fep-service presents to card-service: the OAuth 2.0
 * {@code client_credentials} grant (RFC 6749 §4.4) for Keycloak client {@code cba-fep-service},
 * whose service account holds only the realm role FEP_SERVICE.
 *
 * <p>Hand-written because fep-service is on Spring Security 6.2, which lacks the
 * {@code OAuth2ClientHttpRequestInterceptor} the backend uses (6.4+). One token is shared and
 * fetched again {@link #REFRESH_BEFORE} before it expires. A token that can't be obtained
 * surfaces as a {@link ResourceAccessException} — a {@link RestClientException}, which
 * {@link com.cba.fep.auth.CardServiceClient} already turns into a decline (RC 96) rather than
 * a crash. The token itself is never logged.
 */
public class CardServiceTokenProvider {

    static final Duration REFRESH_BEFORE = Duration.ofSeconds(30);

    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final RestTemplate http;
    private final Clock clock;

    private String token;
    private Instant refreshAt = Instant.MIN;

    public CardServiceTokenProvider(String clientId, String clientSecret, String tokenUri, RestTemplate http, Clock clock) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.http = http;
        this.clock = clock;
    }

    /** A valid access token, fetched when none is cached or the cached one is about to expire. */
    public synchronized String token() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(refreshAt)) {
            return token;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // RFC 6749 §2.3.1: client id and secret are form-encoded before HTTP Basic.
        headers.setBasicAuth(formEncode(clientId), formEncode(clientSecret), StandardCharsets.UTF_8);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        Map<?, ?> body;
        try {
            body = http.postForObject(tokenUri, new HttpEntity<>(form, headers), Map.class);
        } catch (RestClientException e) {
            throw new ResourceAccessException("No card-service token from " + tokenUri + ": " + e.getMessage());
        }
        Object accessToken = body == null ? null : body.get("access_token");
        if (!(accessToken instanceof String value) || value.isBlank()) {
            throw new ResourceAccessException("No card-service token from " + tokenUri + ": no access_token in the response");
        }
        long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 60;
        token = value;
        refreshAt = now.plusSeconds(expiresIn).minus(REFRESH_BEFORE);
        return token;
    }

    private static String formEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

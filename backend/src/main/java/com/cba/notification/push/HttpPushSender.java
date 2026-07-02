package com.cba.notification.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real push sender — POSTs {@code {token, title, body, data}} to a configurable HTTP relay
 * (an FCM proxy, a push microservice, or a vendor gateway). Active only when
 * {@code app.push.provider=HTTP}; dormant until credentials are supplied.
 *
 * <p>A 404/410 (or FCM {@code UNREGISTERED}) marks the token dead → {@link PushResult#invalidToken}
 * so the dispatch layer deactivates it. Any other non-2xx / transport error → rejected. Never throws.
 */
@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "HTTP")
public class HttpPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(HttpPushSender.class);

    private final RestTemplate http;
    private final String url;
    private final String apiKey;

    public HttpPushSender(RestTemplateBuilder builder, Environment env) {
        this(builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(8)).build(),
             env.getProperty("app.push.http.url", ""),
             env.getProperty("app.push.http.api-key", ""));
    }

    /** Test seam — inject a {@link RestTemplate} bound to {@code MockRestServiceServer}. */
    HttpPushSender(RestTemplate http, String url, String apiKey) {
        this.http = http;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public PushResult send(String token, String title, String body, Map<String, String> data) {
        if (url == null || url.isBlank()) {
            return PushResult.rejected("NO_PUSH_URL", "app.push.http.url is not configured");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("data", data == null ? Map.of() : data);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        try {
            ResponseEntity<Map> resp = http.postForEntity(url, new HttpEntity<>(payload, headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return PushResult.accepted(extractId(resp.getBody()));
            }
            return PushResult.rejected("HTTP_" + resp.getStatusCode().value(),
                    "Push relay returned " + resp.getStatusCode());
        } catch (HttpStatusCodeException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND || status == HttpStatus.GONE) {
                return PushResult.invalidToken("Token rejected: " + status);
            }
            return PushResult.rejected("HTTP_" + e.getStatusCode().value(), e.getStatusText());
        } catch (Exception e) {
            log.warn("[PUSH:HTTP] send to {} failed: {}", NoOpPushSender.mask(token), e.toString());
            return PushResult.rejected("TRANSPORT_ERROR", e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return "HTTP";
    }

    private static String extractId(Map<?, ?> body) {
        if (body != null) {
            for (String key : new String[]{"messageId", "name", "id", "message_id"}) {
                Object v = body.get(key);
                if (v != null) return String.valueOf(v);
            }
        }
        return "http-accepted";
    }
}

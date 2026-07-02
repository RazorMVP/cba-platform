package com.cba.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real SMS gateway adapter — POSTs a JSON payload to a configurable HTTP endpoint.
 * Active only when {@code app.sms.provider=HTTP}; otherwise {@link NoOpSmsProvider}
 * stays in charge, so this code path is dormant until credentials are supplied.
 *
 * <p>Deliberately gateway-agnostic: the {@code {to, from, message}} JSON body and
 * {@code Authorization: Bearer <api-key>} header fit Twilio-compatible gateways,
 * Africa's Talking, Infobip, and most in-house aggregators. Vendors with a different
 * body shape need only a sibling implementation behind a new {@code havingValue} —
 * no change to {@code SmsDispatchService} or campaign logic (same isolation as the
 * settlement-file exporters).
 *
 * <p>A non-2xx response or any transport error is a <em>rejection</em>, never an
 * exception out of {@link #send} — the dispatch layer records {@code FAILED} and the
 * caller is never destabilised by a flaky gateway.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "HTTP")
public class HttpSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpSmsProvider.class);

    private final RestTemplate http;
    private final String url;
    private final String apiKey;
    private final String sender;

    public HttpSmsProvider(RestTemplateBuilder builder,
                           org.springframework.core.env.Environment env) {
        this(builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(8)).build(),
             env.getProperty("app.sms.http.url", ""),
             env.getProperty("app.sms.http.api-key", ""),
             env.getProperty("app.sms.http.sender", "CBA"));
    }

    /** Test seam — inject a {@link RestTemplate} bound to {@code MockRestServiceServer}. */
    HttpSmsProvider(RestTemplate http, String url, String apiKey, String sender) {
        this.http = http;
        this.url = url;
        this.apiKey = apiKey;
        this.sender = sender;
    }

    @Override
    public SmsResult send(String toPhoneNumber, String message) {
        if (url == null || url.isBlank()) {
            return SmsResult.rejected("NO_GATEWAY_URL", "app.sms.http.url is not configured");
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("to", toPhoneNumber);
        body.put("from", sender);
        body.put("message", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        try {
            ResponseEntity<Map> resp = http.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return SmsResult.accepted(extractId(resp.getBody()));
            }
            return SmsResult.rejected("HTTP_" + resp.getStatusCode().value(),
                    "Gateway returned " + resp.getStatusCode());
        } catch (Exception e) {
            // DNS failure, connection refused, timeout, non-2xx from a strict gateway, etc.
            log.warn("[SMS:HTTP] send to {} failed: {}", NoOpSmsProvider.mask(toPhoneNumber), e.toString());
            return SmsResult.rejected("TRANSPORT_ERROR", e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return "HTTP";
    }

    /** Best-effort extraction of a gateway message id; falls back to a synthetic marker. */
    private static String extractId(Map<?, ?> body) {
        if (body != null) {
            for (String key : new String[]{"messageId", "message_id", "id", "sid", "reference"}) {
                Object v = body.get(key);
                if (v != null) return String.valueOf(v);
            }
        }
        return "http-accepted";
    }
}

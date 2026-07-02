package com.cba.system.bureau;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
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
 * Real credit-bureau adapter — POSTs a lookup to a configurable HTTP endpoint. Active only
 * when {@code app.creditbureau.provider=HTTP}; dormant until credentials are supplied.
 *
 * <p>Bureau-agnostic request/response shape: {@code {nationalId, fullName, country}} in,
 * a JSON body with a numeric {@code score} out. Bureaus with a different contract get a
 * sibling implementation behind a new {@code havingValue} — no change to
 * {@link com.cba.system.CreditBureauCheckService}. Any transport error or non-2xx maps to
 * {@link CreditReport#unavailable} — never an exception.
 */
@Component
@ConditionalOnProperty(name = "app.creditbureau.provider", havingValue = "HTTP")
public class HttpCreditBureauProvider implements CreditBureauProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpCreditBureauProvider.class);

    private final RestTemplate http;
    private final String url;
    private final String apiKey;

    public HttpCreditBureauProvider(RestTemplateBuilder builder, Environment env) {
        this(builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(8)).build(),
             env.getProperty("app.creditbureau.http.url", ""),
             env.getProperty("app.creditbureau.http.api-key", ""));
    }

    /** Test seam — inject a {@link RestTemplate} bound to {@code MockRestServiceServer}. */
    HttpCreditBureauProvider(RestTemplate http, String url, String apiKey) {
        this.http = http;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public CreditReport pull(CreditCheckRequest request) {
        if (url == null || url.isBlank()) {
            return CreditReport.unavailable("app.creditbureau.http.url is not configured");
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("nationalId", request.nationalId());
        body.put("fullName", request.fullName());
        body.put("country", request.country());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        try {
            ResponseEntity<Map> resp = http.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return CreditReport.unavailable("Bureau returned " + resp.getStatusCode());
            }
            Map<?, ?> b = resp.getBody();
            Integer score = readScore(b);
            String reference = readString(b, "reference", "id", "referenceId");
            if (score == null) {
                return CreditReport.noHit(reference);
            }
            return CreditReport.hit(score, reference);
        } catch (Exception e) {
            log.warn("[CREDIT_BUREAU:HTTP] pull failed: {}", e.toString());
            return CreditReport.unavailable(e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return "HTTP";
    }

    private static Integer readScore(Map<?, ?> body) {
        if (body == null) return null;
        for (String key : new String[]{"score", "creditScore", "value"}) {
            Object v = body.get(key);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s && s.matches("-?\\d+")) return Integer.parseInt(s);
        }
        return null;
    }

    private static String readString(Map<?, ?> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null) return String.valueOf(v);
        }
        return null;
    }
}

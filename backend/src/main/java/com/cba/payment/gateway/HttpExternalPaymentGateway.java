package com.cba.payment.gateway;

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
 * Real external-payment gateway adapter — POSTs the instruction to a configurable HTTP
 * endpoint (a payment-service-provider / correspondent-bank API). Active only when
 * {@code app.payments.external.gateway=HTTP}; dormant until credentials are supplied.
 *
 * <p>Gateway-agnostic JSON body; a numeric-or-string {@code networkReference}/{@code uetr}
 * in the 2xx response marks acceptance. Any non-2xx or transport error is a
 * {@link GatewayResult#rejected} — {@code PaymentService} then rolls back the debit.
 */
@Component
@ConditionalOnProperty(name = "app.payments.external.gateway", havingValue = "HTTP")
public class HttpExternalPaymentGateway implements ExternalPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpExternalPaymentGateway.class);

    private final RestTemplate http;
    private final String url;
    private final String apiKey;

    public HttpExternalPaymentGateway(RestTemplateBuilder builder, Environment env) {
        this(builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(10)).build(),
             env.getProperty("app.payments.external.http.url", ""),
             env.getProperty("app.payments.external.http.api-key", ""));
    }

    /** Test seam — inject a {@link RestTemplate} bound to {@code MockRestServiceServer}. */
    HttpExternalPaymentGateway(RestTemplate http, String url, String apiKey) {
        this.http = http;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public GatewayResult submit(ExternalPaymentInstruction instruction) {
        if (url == null || url.isBlank()) {
            return GatewayResult.rejected("NO_GATEWAY_URL", "app.payments.external.http.url is not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("network", instruction.network());
        body.put("amount", instruction.amount());
        body.put("currency", instruction.currencyCode());
        body.put("beneficiaryName", instruction.beneficiaryName());
        body.put("beneficiaryIban", instruction.beneficiaryIban());
        body.put("beneficiaryBic", instruction.beneficiaryBic());
        body.put("beneficiaryBankName", instruction.beneficiaryBankName());
        body.put("beneficiaryCountry", instruction.beneficiaryCountryCode());
        body.put("chargeBearer", instruction.chargeType());
        body.put("reference", instruction.reference());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        try {
            ResponseEntity<Map> resp = http.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return GatewayResult.accepted(extractRef(resp.getBody()));
            }
            return GatewayResult.rejected("HTTP_" + resp.getStatusCode().value(),
                    "Gateway returned " + resp.getStatusCode());
        } catch (Exception e) {
            log.warn("[EXT_PAY:HTTP] submit failed: {}", e.toString());
            return GatewayResult.rejected("TRANSPORT_ERROR", e.getMessage());
        }
    }

    @Override
    public String gatewayId() {
        return "HTTP";
    }

    private static String extractRef(Map<?, ?> body) {
        if (body != null) {
            for (String key : new String[]{"networkReference", "uetr", "reference", "id", "trn"}) {
                Object v = body.get(key);
                if (v != null) return String.valueOf(v);
            }
        }
        return "http-accepted";
    }
}

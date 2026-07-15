package com.cba.fep.auth;

import com.cba.fep.scheme.SchemeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * REST client for card-service communication.
 *
 * <p>The FEP calls card-service for:
 * <ul>
 *   <li>Authorization decisions (fraud scoring + limit checks + account balance)</li>
 *   <li>Advice recording (single-message completions)</li>
 *   <li>Reversals (idempotent credit back to account)</li>
 *   <li>Token de-tokenization (DPAN → PAN)</li>
 *   <li>BIN lookup (scheme resolution from PAN prefix)</li>
 * </ul>
 *
 * <p>All calls use a shared {@link RestTemplate}. In production this should
 * be replaced with a reactive WebClient for non-blocking I/O, or use
 * connection pooling with circuit breaker (Resilience4j) to handle card-service
 * outages gracefully (falling back to stand-in processing).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardServiceClient {

    @Value("${fep.card-service.base-url:http://localhost:8081}")
    private String cardServiceBaseUrl;

    private final RestTemplate restTemplate;

    /**
     * Send authorization request to card-service and receive the decision.
     *
     * @param request authorization data from the ISO 8583 message
     * @return authorization result with response code and auth code
     */
    public AuthorizationResult authorize(AuthorizationRequest request) {
        try {
            HttpHeaders headers = jsonHeaders();
            Map<String, Object> body = new HashMap<>();
            body.put("pan",             request.pan());
            body.put("processingCode",  request.processingCode());
            body.put("amount",          request.amount());
            body.put("currencyCode",    request.currencyCode());
            body.put("stan",            request.stan());
            body.put("rrn",             request.rrn());
            body.put("terminalId",      request.terminalId());
            body.put("merchantId",      request.merchantId());
            body.put("merchantName",    request.merchantName());
            body.put("mcc",             request.mcc());
            body.put("posEntryMode",    request.posEntryMode());
            body.put("scheme",          request.scheme() != null ? request.scheme().name() : null);
            body.put("pinVerified",     request.pinVerified());
            body.put("arqcValid",       request.arqcValid());
            body.put("isFinancial",     request.isFinancial());
            body.put("schemeData",      request.schemeData());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    cardServiceBaseUrl + "/api/v1/fep/authorize",
                    new HttpEntity<>(body, headers),
                    Map.class);

            return parseAuthResult(response);
        } catch (RestClientException e) {
            log.error("card-service auth call failed for STAN={}: {}", request.stan(), e.getMessage());
            return AuthorizationResult.systemError();
        }
    }

    /**
     * Record a single-message advice (0120 / 0220).
     * The transaction was already completed at the terminal.
     */
    public AuthorizationResult recordAdvice(String pan, String amount, String stan) {
        try {
            Map<String, Object> body = Map.of(
                    "pan", pan != null ? pan : "",
                    "amount", amount != null ? amount : "0",
                    "stan", stan != null ? stan : "");
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    cardServiceBaseUrl + "/api/v1/fep/advice",
                    new HttpEntity<>(body, jsonHeaders()),
                    Map.class);
            return parseAuthResult(response);
        } catch (RestClientException e) {
            log.error("card-service advice call failed for STAN={}: {}", stan, e.getMessage());
            return AuthorizationResult.systemError();
        }
    }

    /**
     * Record a reversal (0400 / 0420).
     * Idempotent — duplicate reversals do not double-credit.
     *
     * @return RC=00 if accepted, RC=25 if original not found, RC=96 if error
     */
    public String reverse(String pan, String amount, String stan, String originalDataElements) {
        try {
            Map<String, Object> body = Map.of(
                    "pan",                  pan != null ? pan : "",
                    "amount",               amount != null ? amount : "0",
                    "stan",                 stan != null ? stan : "",
                    "originalDataElements", originalDataElements != null ? originalDataElements : "");
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    cardServiceBaseUrl + "/api/v1/internal/reverse",
                    new HttpEntity<>(body, jsonHeaders()),
                    Map.class);
            return response != null ? (String) response.get("responseCode") : "96";
        } catch (RestClientException e) {
            log.error("card-service reversal call failed for STAN={}: {}", stan, e.getMessage());
            return "96";
        }
    }

    /**
     * De-tokenize a DPAN (device PAN / token) to the real PAN.
     * Called when DE2 contains a token BIN prefix (9999xx).
     */
    public String detokenize(String dpan) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    cardServiceBaseUrl + "/api/v1/fep/detokenize",
                    new HttpEntity<>(Map.of("dpan", dpan), jsonHeaders()),
                    Map.class);
            return response != null ? (String) response.get("pan") : dpan;
        } catch (RestClientException e) {
            log.warn("Detokenization failed for DPAN prefix {}: {}", dpan.substring(0, 6), e.getMessage());
            return dpan; // Fall back to DPAN — card-service will decline with correct RC
        }
    }

    /**
     * Lookup the card scheme for a given BIN prefix (6 or 8 digits).
     * Used as fallback when the local BIN cache has no entry.
     */
    public SchemeType lookupBinScheme(String binPrefix) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    cardServiceBaseUrl + "/api/v1/bins/{bin}/scheme",
                    Map.class,
                    binPrefix);
            if (response != null && response.containsKey("scheme")) {
                return SchemeType.valueOf((String) response.get("scheme"));
            }
        } catch (Exception e) {
            log.warn("BIN scheme lookup failed for prefix {}: {}", binPrefix, e.getMessage());
        }
        return SchemeType.UNKNOWN;
    }

    /**
     * Retrieve the full BIN-to-scheme mapping for cache pre-population.
     * Returns a map of BIN prefix → SchemeType.
     */
    public Map<String, SchemeType> getAllBinMappings() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> response = restTemplate.getForObject(
                    cardServiceBaseUrl + "/api/v1/bins/all",
                    Map.class);
            if (response == null) return Collections.emptyMap();
            Map<String, SchemeType> result = new HashMap<>();
            response.forEach((bin, scheme) -> {
                try {
                    result.put(bin, SchemeType.valueOf(scheme));
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown scheme '{}' for BIN {}", scheme, bin);
                }
            });
            return result;
        } catch (RestClientException e) {
            log.warn("Failed to fetch BIN mappings from card-service: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private AuthorizationResult parseAuthResult(Map<String, Object> response) {
        if (response == null) return AuthorizationResult.systemError();
        String rc       = (String)  response.get("responseCode");
        String authCode = (String)  response.get("authorizationCode");
        Boolean standIn = (Boolean) response.get("standIn");
        return new AuthorizationResult(
                rc != null ? rc : "96",
                authCode,
                "00".equals(rc),
                Boolean.TRUE.equals(standIn),
                null, null, null);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

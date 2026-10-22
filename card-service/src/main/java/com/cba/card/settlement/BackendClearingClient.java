package com.cba.card.settlement;

import com.cba.card.config.RestClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Clears a debit-card authorization on the backend: {@code POST /api/v1/accounts/{id}/card-holds/{ref}/capture}
 * turns the authorization's hold into a debit and posts DR deposit / CR 2300 Card Scheme Settlement
 * Payable. Idempotent on the backend (the same amount again returns the first result), so a retry
 * after a failure on this side never debits twice. Uses the service-token client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendClearingClient {

    private final RestTemplate backendRestTemplate;
    private final RestClientConfig restClientConfig;

    /** The backend's answer; {@code reason} is set when {@code approved} is false. */
    public record Result(boolean approved, String status, String reason) {}

    /** The backend's {@code CardHoldResult} — the fields read here. */
    record BackendHold(boolean approved, String status, String reason) {}

    /** The backend's {@code ApiResponse} envelope — only {@code data} is read. */
    record Envelope<T>(T data) {}

    /**
     * @param amountMajor  major units of {@code currencyAlpha}
     * @return the backend's result, or null when it couldn't be reached (the caller retries later)
     */
    public Result capture(UUID accountId, String holdReference, BigDecimal amountMajor, String currencyAlpha) {
        try {
            String url = restClientConfig.getBackendBaseUrl() + "/api/v1/accounts/" + accountId
                    + "/card-holds/" + holdReference + "/capture";
            ResponseEntity<Envelope<BackendHold>> resp = backendRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(Map.of("amount", amountMajor, "currencyCode", currencyAlpha)),
                    new ParameterizedTypeReference<Envelope<BackendHold>>() {});
            BackendHold hold = resp.getBody() != null ? resp.getBody().data() : null;
            return hold == null ? null : new Result(hold.approved(), hold.status(), hold.reason());
        } catch (RestClientException e) {
            log.warn("Clearing of hold {} on account {} failed: {}", holdReference, accountId, e.getMessage());
            return null;
        }
    }
}

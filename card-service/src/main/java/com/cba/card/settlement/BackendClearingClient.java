package com.cba.card.settlement;

import com.cba.card.config.RestClientConfig;
import com.cba.card.interchange.InterchangeDirection;
import com.cba.card.interchange.IssuerCharges;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * card-service's calls to the backend for clearing and settlement, with the service-token client:
 * <ul>
 *   <li>{@code POST /api/v1/accounts/{id}/card-holds/{ref}/capture} turns an authorization's hold into a
 *       debit and posts DR deposit / CR 2300, with the interchange and scheme fees in the same journal
 *       (backlog 15b). Idempotent (the same amount again returns the first result and figures), so a
 *       retry never debits twice.</li>
 *   <li>{@code POST /api/v1/card-settlements} posts a scheme's net settlement (DR 2300 / CR 1003),
 *       idempotent on the reference.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendClearingClient {

    private final RestTemplate backendRestTemplate;
    private final RestClientConfig restClientConfig;

    /**
     * The backend's answer; {@code reason} is set when {@code approved} is false. The figures are what the
     * backend posted (major units; null when none), authoritative over what was sent.
     */
    public record Result(boolean approved, String status, String reason,
                         BigDecimal interchangeAmount, String interchangeDirection, BigDecimal schemeFeeAmount) {

        public Result(boolean approved, String status, String reason) {
            this(approved, status, reason, null, null, null);
        }

        /** The posted figures for {@code scheme}. */
        public IssuerCharges posted(String scheme) {
            InterchangeDirection direction = interchangeDirection == null ? null : InterchangeDirection.valueOf(interchangeDirection);
            return new IssuerCharges(scheme, interchangeAmount, direction, schemeFeeAmount, "BACKEND");
        }
    }

    /** The backend's {@code CardHoldResult} — the fields read here. */
    record BackendHold(boolean approved, String status, String reason,
                       BigDecimal interchangeAmount, String interchangeDirection, BigDecimal schemeFeeAmount) {}

    /** The backend's {@code CardSchemeSettlementResult}; null from {@link #recordSchemeSettlement} when unreachable. */
    public record SettlementPost(boolean approved, boolean repeat, String reference,
                                 String journalTransactionId, String reason) {}

    /** The backend's {@code ApiResponse} envelope — only {@code data} is read. */
    record Envelope<T>(T data) {}

    /** A capture without interchange or fees. */
    public Result capture(UUID accountId, String holdReference, BigDecimal amountMajor, String currencyAlpha) {
        return capture(accountId, holdReference, amountMajor, currencyAlpha, null);
    }

    /**
     * @param amountMajor major units of {@code currencyAlpha}
     * @param charges     interchange and scheme fees (major units), posted in the same journal; null = none
     * @return the backend's result, or null when it couldn't be reached (the caller retries later)
     */
    public Result capture(UUID accountId, String holdReference, BigDecimal amountMajor, String currencyAlpha,
                          IssuerCharges charges) {
        try {
            String url = restClientConfig.getBackendBaseUrl() + "/api/v1/accounts/" + accountId
                    + "/card-holds/" + holdReference + "/capture";
            Map<String, Object> body = new HashMap<>();
            body.put("amount", amountMajor);
            body.put("currencyCode", currencyAlpha);
            putCharges(body, charges);
            ResponseEntity<Envelope<BackendHold>> resp = backendRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body), new ParameterizedTypeReference<Envelope<BackendHold>>() {});
            BackendHold hold = resp.getBody() != null ? resp.getBody().data() : null;
            return hold == null ? null : new Result(hold.approved(), hold.status(), hold.reason(),
                    hold.interchangeAmount(), hold.interchangeDirection(), hold.schemeFeeAmount());
        } catch (RestClientException e) {
            log.warn("Clearing of hold {} on account {} failed: {}", holdReference, accountId, e.getMessage());
            return null;
        }
    }

    /**
     * Posts a scheme's net settlement on the backend (the cash leg).
     *
     * @param amountMajor signed: positive = the bank pays the scheme
     * @return the backend's result, or null when it couldn't be reached (the report stays POSTING; a retry finishes it)
     */
    public SettlementPost recordSchemeSettlement(String reference, String scheme, LocalDate settlementDate,
                                                 String currencyAlpha, BigDecimal amountMajor) {
        try {
            Map<String, Object> body = Map.of("reference", reference, "scheme", scheme,
                    "settlementDate", settlementDate.toString(), "currencyCode", currencyAlpha, "amount", amountMajor);
            ResponseEntity<Envelope<SettlementPost>> resp = backendRestTemplate.exchange(
                    restClientConfig.getBackendBaseUrl() + "/api/v1/card-settlements", HttpMethod.POST,
                    new HttpEntity<>(body), new ParameterizedTypeReference<Envelope<SettlementPost>>() {});
            return resp.getBody() != null ? resp.getBody().data() : null;
        } catch (RestClientException e) {
            log.warn("Scheme settlement {} could not reach the backend: {}", reference, e.getMessage());
            return null;
        }
    }

    /** Adds the fee figures to a request body (the backend treats absent fields as none). */
    static void putCharges(Map<String, Object> body, IssuerCharges charges) {
        if (charges == null) return;
        body.put("scheme", charges.scheme());
        if (charges.interchange().signum() > 0) {
            body.put("interchangeAmount", charges.interchange());
            body.put("interchangeDirection", charges.direction() == null ? null : charges.direction().name());
        }
        if (charges.schemeFee().signum() > 0) body.put("schemeFeeAmount", charges.schemeFee());
    }
}

package com.cba.openbanking.card;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST client for card-service (:8081).
 *
 * <p>All methods are fail-safe — a card-service outage degrades gracefully:
 * AISP account lists simply omit card accounts; balance/transaction lookups
 * return empty. This prevents card-service unavailability from breaking the
 * existing banking Open Banking endpoints.
 *
 * <p>Response shapes mirror card-service's {@code ApiResponse<T>} envelope:
 * <pre>{ "data": T, "meta": {}, "errors": [] }</pre>
 */
@Slf4j
@Component
public class CardServiceClient {

    private final RestTemplate rest;

    public CardServiceClient(@Qualifier("cardServiceRestTemplate") RestTemplate rest) {
        this.rest = rest;
    }

    // ── DTOs (mirror card-service internal shapes needed for OB mapping) ──────

    public record CardDto(
            UUID id,
            String cardType,        // DEBIT | PREPAID | CREDIT
            String status,
            String panPrefix,
            String panSuffix,
            String expiryDate,
            UUID customerId,
            UUID linkedEntityId,
            String productName
    ) {}

    public record CardBalanceDto(
            BigDecimal availableBalance,
            String cardType
    ) {}

    public record CardAuthDto(
            UUID id,
            String stan,
            String rrn,
            BigDecimal amount,
            String currencyCode,
            String responseCode,
            String merchantName,
            String merchantId,
            String mcc,
            boolean financial,
            Instant createdAt
    ) {}

    // ── Query methods ─────────────────────────────────────────────────────────

    /**
     * Returns all cards belonging to a customer.
     * Returns an empty list if card-service is unavailable.
     */
    public List<CardDto> getCardsForCustomer(UUID customerId) {
        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    "/api/v1/cards?customerId=" + customerId,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return extractList(resp.getBody(), CardDto.class);
        } catch (RestClientException e) {
            log.warn("card-service unavailable for customer {}: {}", customerId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns a single card by ID, or empty if not found / card-service unavailable.
     */
    public Optional<CardDto> getCard(UUID cardId) {
        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    "/api/v1/cards/" + cardId,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return Optional.ofNullable(extractSingle(resp.getBody(), CardDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("card-service unavailable for card {}: {}", cardId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the available balance for a card.
     * Routes internally in card-service by card type (wallet / account / credit-line).
     */
    public Optional<CardBalanceDto> getCardBalance(UUID cardId) {
        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    "/api/v1/cards/" + cardId + "/balance",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return Optional.ofNullable(extractSingle(resp.getBody(), CardBalanceDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("card-service balance unavailable for card {}: {}", cardId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns authorization history for a card (approved and declined).
     */
    public List<CardAuthDto> getCardAuthorizations(UUID cardId) {
        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    "/api/v1/cards/" + cardId + "/authorizations",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return extractList(resp.getBody(), CardAuthDto.class);
        } catch (RestClientException e) {
            log.warn("card-service auth history unavailable for card {}: {}", cardId, e.getMessage());
            return List.of();
        }
    }

    // ── Response deserialization helpers ──────────────────────────────────────

    /**
     * Extracts {@code data} from an {@code ApiResponse<T>} envelope and maps it to {@code type}.
     * card-service wraps every response as {@code { "data": ..., "meta": {}, "errors": [] }}.
     */
    @SuppressWarnings("unchecked")
    private <T> T extractSingle(Map<String, Object> body, Class<T> type) {
        if (body == null) return null;
        Object data = body.get("data");
        if (data == null) return null;
        // Jackson deserializes nested objects as LinkedHashMap; we re-map to the record manually
        if (data instanceof Map<?, ?> map) {
            return mapToDto((Map<String, Object>) map, type);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> extractList(Map<String, Object> body, Class<T> type) {
        if (body == null) return List.of();
        Object data = body.get("data");
        if (data instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> mapToDto((Map<String, Object>) item, type))
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private <T> T mapToDto(Map<String, Object> m, Class<T> type) {
        try {
            if (type == CardDto.class) {
                Map<String, Object> product = m.get("product") instanceof Map<?, ?>
                        ? (Map<String, Object>) m.get("product") : Map.of();
                return type.cast(new CardDto(
                        toUuid(m.get("id")),
                        str(m.get("cardType")),
                        str(m.get("status")),
                        str(m.get("panPrefix")),
                        str(m.get("panSuffix")),
                        str(m.get("expiryDate")),
                        toUuid(m.get("customerId")),
                        toUuid(m.get("linkedEntityId")),
                        str(product.get("name"))
                ));
            }
            if (type == CardBalanceDto.class) {
                return type.cast(new CardBalanceDto(
                        toBigDecimal(m.get("availableBalance")),
                        str(m.get("cardType"))
                ));
            }
            if (type == CardAuthDto.class) {
                return type.cast(new CardAuthDto(
                        toUuid(m.get("id")),
                        str(m.get("stan")),
                        str(m.get("rrn")),
                        toBigDecimal(m.get("amount")),
                        str(m.get("currencyCode")),
                        str(m.get("responseCode")),
                        str(m.get("merchantName")),
                        str(m.get("merchantId")),
                        str(m.get("mcc")),
                        Boolean.TRUE.equals(m.get("financial")),
                        toInstant(m.get("createdAt"))
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to map card-service DTO to {}: {}", type.getSimpleName(), e.getMessage());
        }
        return null;
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private static UUID toUuid(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (IllegalArgumentException e) { return null; }
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        try { return new BigDecimal(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Instant toInstant(Object o) {
        if (o == null) return null;
        try { return Instant.parse(o.toString()); } catch (Exception e) { return null; }
    }
}

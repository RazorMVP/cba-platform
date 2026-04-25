package com.cba.partner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerWebhookDeliveryService {

    private static final int MAX_ATTEMPTS = 5;
    // Backoff schedule: 15s, 60s, 300s, 1800s, 7200s
    private static final long[] BACKOFF_SECONDS = {15, 60, 300, 1800, 7200};

    private final PartnerWebhookRepository webhookRepo;
    private final PartnerWebhookDeliveryRepository deliveryRepo;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Fan out an event to all active webhooks that subscribe to it.
     * Called from PartnerService when a significant event occurs.
     */
    @Async
    @Transactional
    public void publishEvent(UUID orgId, String eventType, Object payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "event", eventType,
                    "timestamp", Instant.now().toString(),
                    "data", payload
            ));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize webhook payload for event {}: {}", eventType, e.getMessage());
            return;
        }

        List<PartnerWebhook> hooks = webhookRepo.findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(orgId);
        for (PartnerWebhook hook : hooks) {
            if (hook.getEvents() != null && !hook.getEvents().contains(eventType)) {
                continue;
            }
            PartnerWebhookDelivery delivery = PartnerWebhookDelivery.builder()
                    .webhook(hook)
                    .eventType(eventType)
                    .deliveryUuid(UUID.randomUUID().toString())
                    .payload(payloadJson)
                    .status("PENDING")
                    .attemptCount(0)
                    .build();
            PartnerWebhookDelivery saved = deliveryRepo.save(delivery);
            attemptDelivery(saved.getId());
        }
    }

    @Async
    public void attemptDelivery(UUID deliveryId) {
        PartnerWebhookDelivery delivery = deliveryRepo.findById(deliveryId).orElse(null);
        if (delivery == null) return;
        dispatch(delivery);
    }

    /** Retry poller — runs every 60 seconds, picks up FAILED deliveries whose next_retry_at has passed. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void retryDueDeliveries() {
        List<PartnerWebhookDelivery> due = deliveryRepo.findDueForRetry(Instant.now());
        for (PartnerWebhookDelivery d : due) {
            dispatch(d);
        }
    }

    @Transactional
    public List<PartnerWebhookDelivery> listDeliveries(UUID webhookId) {
        return deliveryRepo.findByWebhookIdOrderByCreatedAtDesc(webhookId);
    }

    // ── Internal dispatch ──────────────────────────────────────────────────────

    private void dispatch(PartnerWebhookDelivery delivery) {
        PartnerWebhook hook = delivery.getWebhook();
        String payload = delivery.getPayload() != null ? delivery.getPayload() : "{}";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(hook.getCallbackUrl()))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-CBA-Event", delivery.getEventType())
                .header("X-CBA-Delivery", delivery.getDeliveryUuid())
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        if (hook.getSecret() != null && !hook.getSecret().isBlank()) {
            builder.header("X-CBA-Signature", "sha256=" + hmacHex(hook.getSecret(), payload));
        }

        int attempt = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attempt);
        delivery.setLastAttemptAt(Instant.now());

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            delivery.setHttpStatus(response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus("DELIVERED");
                delivery.setNextRetryAt(null);
            } else {
                markFailed(delivery, attempt);
            }
        } catch (java.io.IOException | InterruptedException e) {
            log.debug("Webhook delivery failed (attempt {}): {}", attempt, e.getMessage());
            delivery.setHttpStatus(null);
            markFailed(delivery, attempt);
        }
        deliveryRepo.save(delivery);
    }

    private void markFailed(PartnerWebhookDelivery delivery, int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            delivery.setStatus("FAILED");
            delivery.setNextRetryAt(null);
        } else {
            delivery.setStatus("FAILED");
            delivery.setNextRetryAt(Instant.now().plusSeconds(BACKOFF_SECONDS[attempt - 1]));
        }
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}

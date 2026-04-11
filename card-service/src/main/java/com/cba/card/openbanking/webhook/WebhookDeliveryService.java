package com.cba.card.openbanking.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Async webhook delivery engine with exponential backoff retry.
 *
 * <h3>Delivery flow</h3>
 * <ol>
 *   <li>{@link WebhookService#publishEvent} enqueues a {@link WebhookDeliveryLog} record (status=PENDING)</li>
 *   <li>{@link #deliverAsync} fires immediately via {@code @Async}</li>
 *   <li>On failure, sets status=FAILED + {@code next_retry_at = now + backoff(attempt)}</li>
 *   <li>{@link #retryDueDeliveries} polls every 60 s; picks up records where {@code next_retry_at <= now}</li>
 * </ol>
 *
 * <h3>Backoff schedule</h3>
 * Attempt 1 → +15 s → Attempt 2 → +60 s → Attempt 3 → +300 s → Attempt 4 → +1800 s → Attempt 5 → FAILED permanently
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {

    private static final long[] BACKOFF_SECONDS = {15, 60, 300, 1800, 7200};
    private static final int    MAX_ATTEMPTS    = 5;

    private final WebhookDeliveryLogRepository deliveryRepo;
    private final WebClient                    webClient;

    // ── Immediate delivery ────────────────────────────────────────────────────

    @Async
    @Transactional
    public void deliverAsync(WebhookDeliveryLog delivery) {
        attemptDelivery(delivery);
    }

    // ── Retry picker ──────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60_000)  // every 60 s
    @Transactional
    public void retryDueDeliveries() {
        List<WebhookDeliveryLog> due = deliveryRepo.findDueForRetry(OffsetDateTime.now());
        if (!due.isEmpty()) {
            log.info("Webhook retry: {} deliveries due", due.size());
        }
        for (WebhookDeliveryLog d : due) {
            attemptDelivery(d);
        }
    }

    // ── Core attempt ──────────────────────────────────────────────────────────

    private void attemptDelivery(WebhookDeliveryLog delivery) {
        Webhook webhook = delivery.getWebhook();
        String  payload = delivery.getPayload();
        int     attempt = delivery.getAttemptCount() + 1;

        delivery.setAttemptCount(attempt);
        delivery.setLastAttemptAt(OffsetDateTime.now());

        try {
            String signature = hmacSha256(payload, webhook.getSecret());

            Integer httpStatus = webClient.post()
                    .uri(webhook.getCallbackUrl())
                    .header("Content-Type", "application/json")
                    .header("X-CBA-Event",     delivery.getEventType())
                    .header("X-CBA-Delivery",  delivery.getDeliveryUuid())
                    .header("X-CBA-Signature", "sha256=" + signature)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .map(r -> r.getStatusCode().value())
                    .timeout(Duration.ofSeconds(10))
                    .onErrorReturn(500)
                    .block();

            delivery.setHttpStatus(httpStatus);

            if (httpStatus != null && httpStatus >= 200 && httpStatus < 300) {
                delivery.setStatus("DELIVERED");
                delivery.setNextRetryAt(null);
                log.info("Webhook delivered: id={} event={} status={} url={}",
                        delivery.getId(), delivery.getEventType(), httpStatus, webhook.getCallbackUrl());
            } else {
                scheduleRetry(delivery, attempt);
                log.warn("Webhook delivery failed (HTTP {}): id={} attempt={}/{}",
                        httpStatus, delivery.getId(), attempt, MAX_ATTEMPTS);
            }

        } catch (Exception e) {
            delivery.setHttpStatus(null);
            scheduleRetry(delivery, attempt);
            log.warn("Webhook delivery error: id={} attempt={}/{} error={}",
                    delivery.getId(), attempt, MAX_ATTEMPTS, e.getMessage());
        }

        deliveryRepo.save(delivery);
    }

    private void scheduleRetry(WebhookDeliveryLog delivery, int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            delivery.setStatus("FAILED");
            delivery.setNextRetryAt(null);
            log.error("Webhook permanently failed after {} attempts: id={} event={}",
                    MAX_ATTEMPTS, delivery.getId(), delivery.getEventType());
        } else {
            delivery.setStatus("FAILED");
            long delaySecs = BACKOFF_SECONDS[Math.min(attempt, BACKOFF_SECONDS.length - 1)];
            delivery.setNextRetryAt(OffsetDateTime.now().plusSeconds(delaySecs));
        }
    }

    // ── HMAC-SHA256 signing ───────────────────────────────────────────────────

    static String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }
}

package com.cba.card.openbanking.webhook;

import com.cba.card.common.CbaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Manages webhook registrations and publishes events to all matching active webhooks.
 *
 * <h3>Event routing</h3>
 * {@link #publishEvent(String, Object)} fans out to every active webhook whose
 * {@code events} list contains the given event type, OR whose list is empty
 * (empty list = subscribe to all events).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository            webhookRepo;
    private final WebhookDeliveryLogRepository deliveryRepo;
    private final WebhookDeliveryService       deliveryService;
    private final ObjectMapper                 objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional
    public RegisterResult register(String name, String callbackUrl,
                                   List<String> events, UUID createdBy) {
        // Generate a secret — shown once, stored plaintext for HMAC computation
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        Webhook webhook = new Webhook();
        webhook.setName(name);
        webhook.setCallbackUrl(callbackUrl);
        webhook.setEvents(events != null ? events : List.of());
        webhook.setSecret(secret);
        webhook.setCreatedBy(createdBy);
        webhook = webhookRepo.save(webhook);

        log.info("Webhook registered: id={} name={} events={}", webhook.getId(), name, events);
        return new RegisterResult(webhook, secret);
    }

    @Transactional(readOnly = true)
    public List<Webhook> listActive() {
        return webhookRepo.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryLog> listDeliveries(UUID webhookId) {
        // Validate webhook exists
        webhookRepo.findById(webhookId)
                .orElseThrow(() -> CbaException.notFound("WEBHOOK_NOT_FOUND", "Webhook not found: " + webhookId));
        return deliveryRepo.findByWebhookIdOrderByCreatedAtDesc(webhookId);
    }

    @Transactional
    public void deregister(UUID id) {
        Webhook webhook = webhookRepo.findById(id)
                .orElseThrow(() -> CbaException.notFound("WEBHOOK_NOT_FOUND", "Webhook not found: " + id));
        webhook.setActive(false);
        webhookRepo.save(webhook);
        log.info("Webhook deregistered: id={} name={}", id, webhook.getName());
    }

    // ── Event publishing ──────────────────────────────────────────────────────

    /**
     * Publish an event to all subscribed active webhooks.
     * Each delivery runs asynchronously — this method returns immediately.
     *
     * @param eventType event identifier, e.g. {@code AUTHORIZATION.APPROVED}
     * @param payload   event data object (serialized to JSON)
     */
    @Transactional
    public void publishEvent(String eventType, Object payload) {
        List<Webhook> candidates = webhookRepo.findByActiveTrue();
        String payloadJson = toJson(payload);

        for (Webhook webhook : candidates) {
            // Fan out to: webhooks with empty events list (= all events) OR matching event type
            if (webhook.getEvents().isEmpty() || webhook.getEvents().contains(eventType)) {
                WebhookDeliveryLog delivery = new WebhookDeliveryLog();
                delivery.setWebhook(webhook);
                delivery.setEventType(eventType);
                delivery.setDeliveryUuid(UUID.randomUUID().toString());
                delivery.setPayload(payloadJson);
                delivery = deliveryRepo.save(delivery);
                deliveryService.deliverAsync(delivery);
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize webhook payload: {}", e.getMessage());
            return "{}";
        }
    }

    /** Result of webhook registration — carries the secret shown once only. */
    public record RegisterResult(Webhook webhook, String secret) {}
}

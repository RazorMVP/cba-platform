package com.cba.config;

import com.cba.partner.PartnerWebhookDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes {@code RATE_LIMIT.WARNING} / {@code RATE_LIMIT.EXCEEDED} partner webhooks when a
 * partner-attributable caller approaches or exceeds its rate limit.
 *
 * <p>{@link RateLimitFilter} runs after authentication, so it hands over the partner org taken
 * from the verified login (partner JWT claims or API-key principal) — nothing is re-parsed or
 * looked up here. Events fire <b>only when a threshold is actually crossed and only once per
 * window</b> (deduped in Redis), so the common request path pays nothing and a partner hammering
 * the API over its limit gets at most one EXCEEDED (and one WARNING) event per window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitEventNotifier {

    /** Warn once remaining drops into the last {@value}·100 % of the window. */
    private static final double WARNING_FRACTION = 0.10;

    private final RateLimitService rateLimitService;
    private final PartnerWebhookDeliveryService webhookDelivery;

    /**
     * Best-effort — never throws into the filter. {@code identity} is the caller's bucket key, used
     * for once-per-window dedup; {@code orgId} is the verified partner org, or {@code null} when the
     * caller isn't a partner (no event).
     */
    public void maybeNotify(HttpServletRequest request, String identity, UUID orgId, RateLimitResult result) {
        try {
            String eventType = classify(result);
            if (eventType == null || orgId == null) return; // within limit, or nobody to notify

            if (!rateLimitService.firstEventInWindow(eventType + ":" + identity)) return;

            webhookDelivery.publishEvent(orgId, eventType, Map.of(
                    "limit", result.limit(),
                    "remaining", result.remaining(),
                    "path", request.getRequestURI()));
        } catch (Exception e) {
            log.debug("Rate-limit event publish skipped: {}", e.getMessage());
        }
    }

    private String classify(RateLimitResult result) {
        if (!result.allowed()) return "RATE_LIMIT.EXCEEDED";
        long warnAt = Math.max(1L, (long) (result.limit() * WARNING_FRACTION));
        if (result.remaining() <= warnAt) return "RATE_LIMIT.WARNING";
        return null;
    }
}

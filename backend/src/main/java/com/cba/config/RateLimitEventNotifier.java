package com.cba.config;

import com.cba.partner.PartnerApiKeyRepository;
import com.cba.partner.PartnerApiKeys;
import com.cba.partner.PartnerWebhookDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes {@code RATE_LIMIT.WARNING} / {@code RATE_LIMIT.EXCEEDED} partner webhooks when a
 * partner-attributable caller approaches or exceeds its rate limit.
 *
 * <p>This event was historically deferred because {@link RateLimitFilter} runs <em>before</em>
 * partner authentication, so no orgId is in the {@code SecurityContext} yet. Rather than move the
 * filter, this resolves the org directly from the request — the partner JWT's {@code orgId} claim,
 * or an API-key → org lookup — and does so <b>only when a threshold is actually crossed and only
 * once per window</b> (deduped in Redis). So the common request path pays nothing, and a partner
 * hammering the API over its limit gets at most one EXCEEDED (and one WARNING) event per window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitEventNotifier {

    /** Warn once remaining drops into the last {@value}·100 % of the window. */
    private static final double WARNING_FRACTION = 0.10;

    private final RateLimitService rateLimitService;
    private final PartnerWebhookDeliveryService webhookDelivery;
    private final PartnerApiKeyRepository apiKeyRepository;

    /**
     * Best-effort — never throws into the filter. {@code identity} is the stable per-caller key the
     * filter already computed; it is used for once-per-window dedup so the org lookup and webhook
     * publish happen at most once per window (never on the hot path).
     */
    public void maybeNotify(HttpServletRequest request, String identity, RateLimitResult result) {
        try {
            String eventType = classify(result);
            if (eventType == null) return; // common path — within limit, nothing to do

            // Dedup FIRST (cheap Redis SETNX) so the org resolution + publish are once-per-window.
            if (!rateLimitService.firstEventInWindow(eventType + ":" + identity)) return;

            UUID orgId = resolveOrg(request);
            if (orgId == null) return; // not a partner-attributable caller (no event to send)

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

    /** Partner org from the JWT {@code orgId} claim or an API-key lookup; {@code null} if neither. */
    private UUID resolveOrg(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null) return null;
        if (auth.startsWith("Bearer ")) {
            return parseUuid(extractJwtClaim(auth.substring(7), "orgId"));
        }
        if (auth.startsWith("ApiKey ")) {
            String rawKey = auth.substring("ApiKey ".length()).trim();
            if (rawKey.isEmpty()) return null;
            return apiKeyRepository.findByKeyHashAndActiveTrue(PartnerApiKeys.hash(rawKey))
                    .map(k -> k.getOrganization() != null ? k.getOrganization().getId() : null)
                    .orElse(null);
        }
        return null;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Extract a string claim from a JWT payload without signature verification (verified downstream). */
    private static String extractJwtClaim(String token, String claim) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String search = "\"" + claim + "\":\"";
            int start = payload.indexOf(search);
            if (start < 0) return null;
            start += search.length();
            int end = payload.indexOf('"', start);
            return end > start ? payload.substring(start, end) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

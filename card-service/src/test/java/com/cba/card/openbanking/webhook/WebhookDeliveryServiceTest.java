package com.cba.card.openbanking.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the webhook HMAC-SHA256 signature ({@code X-CBA-Signature}). The
 * async delivery itself uses a reactive {@code WebClient} chain and is integration
 * territory; the signature is the security-critical, pure part.
 */
class WebhookDeliveryServiceTest {

    @Test
    @DisplayName("HMAC-SHA256 matches the canonical RFC test vector")
    void canonicalVector() {
        // HMAC-SHA256(key="key", msg="The quick brown fox jumps over the lazy dog")
        // hmacSha256(payload, secret) — payload is the message, secret is the key
        String sig = WebhookDeliveryService.hmacSha256(
                "The quick brown fox jumps over the lazy dog", "key");
        assertThat(sig).isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    @DisplayName("signature is deterministic and 64 hex chars")
    void deterministic() {
        String a = WebhookDeliveryService.hmacSha256("{\"event\":\"x\"}", "secret");
        String b = WebhookDeliveryService.hmacSha256("{\"event\":\"x\"}", "secret");
        assertThat(a).isEqualTo(b).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("a different secret yields a different signature")
    void secretMatters() {
        assertThat(WebhookDeliveryService.hmacSha256("payload", "secret1"))
                .isNotEqualTo(WebhookDeliveryService.hmacSha256("payload", "secret2"));
    }

    @Test
    @DisplayName("a different payload yields a different signature")
    void payloadMatters() {
        assertThat(WebhookDeliveryService.hmacSha256("payload1", "secret"))
                .isNotEqualTo(WebhookDeliveryService.hmacSha256("payload2", "secret"));
    }
}

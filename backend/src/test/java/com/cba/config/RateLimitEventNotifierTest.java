package com.cba.config;

import com.cba.partner.PartnerWebhookDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitEventNotifier — RATE_LIMIT.WARNING / EXCEEDED fan-out")
class RateLimitEventNotifierTest {

    @Mock RateLimitService rateLimitService;
    @Mock PartnerWebhookDeliveryService webhookDelivery;

    @InjectMocks RateLimitEventNotifier notifier;

    private static MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI("/api/v1/partners/x/usage");
        return r;
    }

    @Test
    @DisplayName("within the limit → no dedup check, no event")
    void withinLimit_noEvent() {
        notifier.maybeNotify(request(), "partner:o", UUID.randomUUID(), RateLimitResult.allowed(100, 50));

        verify(rateLimitService, never()).firstEventInWindow(any());
        verify(webhookDelivery, never()).publishEvent(any(), any(), any());
    }

    @Test
    @DisplayName("EXCEEDED for a partner → fires RATE_LIMIT.EXCEEDED to that org")
    void exceeded_publishes() {
        UUID org = UUID.randomUUID();
        when(rateLimitService.firstEventInWindow("RATE_LIMIT.EXCEEDED:partner:" + org)).thenReturn(true);

        notifier.maybeNotify(request(), "partner:" + org, org, RateLimitResult.denied(100));

        verify(webhookDelivery).publishEvent(eq(org), eq("RATE_LIMIT.EXCEEDED"), any());
    }

    @Test
    @DisplayName("WARNING (last 10%) for a partner → fires RATE_LIMIT.WARNING")
    void warning_publishes() {
        UUID org = UUID.randomUUID();
        when(rateLimitService.firstEventInWindow("RATE_LIMIT.WARNING:partner:" + org)).thenReturn(true);

        // remaining 5 of 100 → within the last 10% → WARNING
        notifier.maybeNotify(request(), "partner:" + org, org, RateLimitResult.allowed(100, 5));

        verify(webhookDelivery).publishEvent(eq(org), eq("RATE_LIMIT.WARNING"), any());
    }

    @Test
    @DisplayName("already fired this window (dedup) → no event")
    void deduped_noEvent() {
        UUID org = UUID.randomUUID();
        when(rateLimitService.firstEventInWindow("RATE_LIMIT.EXCEEDED:partner:" + org)).thenReturn(false);

        notifier.maybeNotify(request(), "partner:" + org, org, RateLimitResult.denied(100));

        verify(webhookDelivery, never()).publishEvent(any(), any(), any());
    }

    @Test
    @DisplayName("not a partner (no org) → no dedup slot used, no event")
    void nonPartner_noEvent() {
        notifier.maybeNotify(request(), "ip:9.9.9.9", null, RateLimitResult.denied(100));

        verify(rateLimitService, never()).firstEventInWindow(any());
        verify(webhookDelivery, never()).publishEvent(any(), any(), any());
    }
}

package com.cba.config;

import com.cba.partner.PartnerApiKey;
import com.cba.partner.PartnerApiKeyRepository;
import com.cba.partner.PartnerApiKeys;
import com.cba.partner.PartnerOrganization;
import com.cba.partner.PartnerWebhookDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitEventNotifier — RATE_LIMIT.WARNING / EXCEEDED fan-out")
class RateLimitEventNotifierTest {

    @Mock RateLimitService rateLimitService;
    @Mock PartnerWebhookDeliveryService webhookDelivery;
    @Mock PartnerApiKeyRepository apiKeyRepository;

    @InjectMocks RateLimitEventNotifier notifier;

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String partnerJwt(UUID orgId) {
        return b64("{\"alg\":\"HS256\"}") + "." + b64("{\"orgId\":\"" + orgId + "\",\"sub\":\"u1\"}") + ".sig";
    }

    private MockHttpServletRequest request(String authHeader) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI("/api/v1/partners/x/usage");
        if (authHeader != null) r.addHeader("Authorization", authHeader);
        return r;
    }

    @Test
    @DisplayName("within the limit → no dedup check, no event")
    void withinLimit_noEvent() {
        RateLimitResult ok = RateLimitResult.allowed(100, 50);

        notifier.maybeNotify(request("Bearer " + partnerJwt(UUID.randomUUID())), "jwt:u1", ok);

        verify(rateLimitService, never()).firstEventInWindow(any());
        verify(webhookDelivery, never()).publishEvent(any(), any(), any());
    }

    @Test
    @DisplayName("EXCEEDED + partner JWT orgId → fires RATE_LIMIT.EXCEEDED to that org")
    void exceeded_jwt_publishes() {
        UUID org = UUID.randomUUID();
        when(rateLimitService.firstEventInWindow("RATE_LIMIT.EXCEEDED:jwt:u1")).thenReturn(true);

        notifier.maybeNotify(request("Bearer " + partnerJwt(org)), "jwt:u1", RateLimitResult.denied(100));

        verify(webhookDelivery).publishEvent(eq(org), eq("RATE_LIMIT.EXCEEDED"), any());
    }

    @Test
    @DisplayName("WARNING (last 10%) + API key → resolves org via key hash and fires RATE_LIMIT.WARNING")
    void warning_apiKey_publishes() {
        UUID org = UUID.randomUUID();
        PartnerApiKey key = mock(PartnerApiKey.class);
        PartnerOrganization organization = mock(PartnerOrganization.class);
        when(key.getOrganization()).thenReturn(organization);
        when(organization.getId()).thenReturn(org);
        when(apiKeyRepository.findByKeyHashAndActiveTrue(PartnerApiKeys.hash("cba_testkey")))
                .thenReturn(Optional.of(key));
        when(rateLimitService.firstEventInWindow("RATE_LIMIT.WARNING:ip:1.2.3.4")).thenReturn(true);

        // remaining 5 of 100 → within the last 10% → WARNING
        notifier.maybeNotify(request("ApiKey cba_testkey"), "ip:1.2.3.4", RateLimitResult.allowed(100, 5));

        verify(webhookDelivery).publishEvent(eq(org), eq("RATE_LIMIT.WARNING"), any());
    }

    @Test
    @DisplayName("already fired this window (dedup) → no org lookup, no event")
    void deduped_noEvent() {
        when(rateLimitService.firstEventInWindow("RATE_LIMIT.EXCEEDED:jwt:u1")).thenReturn(false);

        notifier.maybeNotify(request("Bearer " + partnerJwt(UUID.randomUUID())), "jwt:u1", RateLimitResult.denied(100));

        verify(apiKeyRepository, never()).findByKeyHashAndActiveTrue(any());
        verify(webhookDelivery, never()).publishEvent(any(), any(), any());
    }

    @Test
    @DisplayName("not partner-attributable (no orgId / no key) → deduped slot consumed but no event")
    void nonPartner_noEvent() {
        when(rateLimitService.firstEventInWindow("RATE_LIMIT.EXCEEDED:ip:9.9.9.9")).thenReturn(true);

        notifier.maybeNotify(request(null), "ip:9.9.9.9", RateLimitResult.denied(100));

        verify(webhookDelivery, never()).publishEvent(any(), any(), any());
    }
}

package com.cba.partner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A suspended partner organisation receives no webhook events (backlog 23b, security review of PR A). */
@DisplayName("PartnerWebhookDeliveryService — no events for a suspended organisation")
class PartnerWebhookSuspensionTest {

    PartnerWebhookRepository webhookRepo = mock(PartnerWebhookRepository.class);
    PartnerWebhookDeliveryRepository deliveryRepo = mock(PartnerWebhookDeliveryRepository.class);
    PartnerOrganizationRepository orgRepo = mock(PartnerOrganizationRepository.class);
    PartnerWebhookDeliveryService service =
            new PartnerWebhookDeliveryService(webhookRepo, deliveryRepo, new ObjectMapper(), orgRepo);

    UUID orgId = UUID.randomUUID();

    private PartnerOrganization org(PartnerStatus status) {
        PartnerOrganization o = new PartnerOrganization();
        o.setId(orgId);
        o.setStatus(status);
        return o;
    }

    @Test
    @DisplayName("suspended: no delivery is created, the org's webhooks are not even read")
    void suspended_noDelivery() {
        when(orgRepo.findById(orgId)).thenReturn(Optional.of(org(PartnerStatus.SUSPENDED)));

        service.publishEvent(orgId, "ACCOUNT.BALANCE_UPDATED", Map.of("accountId", "a1"));

        verifyNoInteractions(webhookRepo, deliveryRepo);
    }

    @Test
    @DisplayName("active: the matching webhook gets a delivery")
    void active_delivers() {
        when(orgRepo.findById(orgId)).thenReturn(Optional.of(org(PartnerStatus.PRODUCTION)));
        PartnerWebhook hook = new PartnerWebhook();
        hook.setEvents(List.of("ACCOUNT.BALANCE_UPDATED"));
        when(webhookRepo.findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(orgId)).thenReturn(List.of(hook));
        when(deliveryRepo.save(any())).thenAnswer(inv -> {
            PartnerWebhookDelivery d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        service.publishEvent(orgId, "ACCOUNT.BALANCE_UPDATED", Map.of("accountId", "a1"));

        verify(deliveryRepo).save(any(PartnerWebhookDelivery.class));
    }
}

package com.cba.partner;

import com.cba.openbanking.ConsentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartnerService — usage aggregation")
class PartnerServiceUsageTest {

    @Mock PartnerOrganizationRepository orgRepo;
    @Mock PartnerUserRepository userRepo;
    @Mock PartnerApiKeyRepository apiKeyRepo;
    @Mock PartnerApplicationRepository applicationRepo;
    @Mock PartnerWebhookRepository webhookRepo;
    @Mock PartnerUsageSnapshotRepository usageRepo;
    @Mock PartnerWebhookDeliveryRepository deliveryRepo;
    @Mock ConsentRepository consentRepo;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock PartnerJwtService jwtService;
    @Mock PartnerWebhookDeliveryService webhookDelivery;

    @InjectMocks PartnerService service;

    @Test
    @DisplayName("getUsage sums counters, merges top endpoints, computes webhook delivery rate")
    void getUsage_aggregates() {
        UUID orgId = UUID.randomUUID();
        PartnerUsageSnapshot d1 = snap(LocalDate.now().minusDays(1), 10, 8, 2, Map.of("GET /a", 6, "POST /b", 4));
        PartnerUsageSnapshot d2 = snap(LocalDate.now(), 5, 5, 0, Map.of("GET /a", 5));
        when(usageRepo.findByOrganizationIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(eq(orgId), any()))
                .thenReturn(List.of(d1, d2));
        when(deliveryRepo.countByOrg(orgId)).thenReturn(10L);
        when(deliveryRepo.countDeliveredByOrg(orgId)).thenReturn(9L);

        Map<String, Object> u = service.getUsage(orgId);

        assertThat(u.get("totalRequests")).isEqualTo(15L);
        assertThat(u.get("successRequests")).isEqualTo(13L);
        assertThat(u.get("failedRequests")).isEqualTo(2L);
        assertThat(u.get("webhookDeliveryRate")).isEqualTo(90.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) u.get("topEndpoints");
        assertThat(top).isNotEmpty();
        assertThat(top.get(0).get("endpoint")).isEqualTo("GET /a"); // 6 + 5 = 11, highest
        assertThat(top.get(0).get("count")).isEqualTo(11);
    }

    @Test
    @DisplayName("getUsage returns zeros when no snapshots exist")
    void getUsage_empty() {
        UUID orgId = UUID.randomUUID();
        when(usageRepo.findByOrganizationIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(eq(orgId), any()))
                .thenReturn(List.of());
        when(deliveryRepo.countByOrg(orgId)).thenReturn(0L);
        when(deliveryRepo.countDeliveredByOrg(orgId)).thenReturn(0L);

        Map<String, Object> u = service.getUsage(orgId);
        assertThat(u.get("totalRequests")).isEqualTo(0L);
        assertThat(u.get("webhookDeliveryRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("approveProduction publishes an APPLICATION.APPROVED webhook event")
    void approveProduction_publishesEvent() {
        UUID orgId = UUID.randomUUID();
        PartnerOrganization org = PartnerOrganization.builder()
                .status(PartnerStatus.PENDING_REVIEW)
                .environment(PartnerEnvironment.SANDBOX)
                .build();
        when(orgRepo.findById(orgId)).thenReturn(java.util.Optional.of(org));

        service.approveProduction(orgId, "admin@cba.com");

        verify(webhookDelivery).publishEvent(eq(orgId), eq("APPLICATION.APPROVED"), any());
    }

    private PartnerUsageSnapshot snap(LocalDate date, int total, int succ, int err, Map<String, Integer> eps) {
        PartnerUsageSnapshot s = new PartnerUsageSnapshot();
        s.setSnapshotDate(date);
        s.setTotalCalls(total);
        s.setSuccessCalls(succ);
        s.setErrorCalls(err);
        s.setTopEndpoints(new HashMap<>(eps));
        return s;
    }
}

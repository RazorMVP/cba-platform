package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.system.bureau.CreditBureauProvider;
import com.cba.system.bureau.CreditCheckRequest;
import com.cba.system.bureau.CreditReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditBureauCheckService — pull + pass/fail policy")
class CreditBureauCheckServiceTest {

    @Mock CreditBureauProvider provider;
    @Mock CreditBureauProductMappingRepository mappingRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks CreditBureauCheckService service;

    private CreditCheckRequest req;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultMinScore", 600);
        req = new CreditCheckRequest(UUID.randomUUID(), "NID-1", "Jane", "KE");
        when(provider.providerId()).thenReturn("SIMULATED");
    }

    private CreditBureauProductMapping mapping(boolean active, boolean mandatory) {
        CreditBureauProductMapping m = new CreditBureauProductMapping();
        m.setActive(active);
        m.setCreditCheckMandatory(mandatory);
        return m;
    }

    @Test
    @DisplayName("HIT at or above threshold passes")
    void hit_aboveThreshold_passes() {
        when(provider.pull(any())).thenReturn(CreditReport.hit(720, "ref"));

        CreditBureauCheckService.CreditCheckResult r = service.check(req, null, null);

        assertThat(r.passed()).isTrue();
        assertThat(r.report().score()).isEqualTo(720);
        assertThat(r.mandatory()).isFalse();
    }

    @Test
    @DisplayName("HIT below threshold fails")
    void hit_belowThreshold_fails() {
        when(provider.pull(any())).thenReturn(CreditReport.hit(500, "ref"));

        assertThat(service.check(req, null, null).passed()).isFalse();
    }

    @Test
    @DisplayName("explicit minScore override is applied")
    void minScoreOverride() {
        when(provider.pull(any())).thenReturn(CreditReport.hit(650, "ref"));

        assertThat(service.check(req, null, 700).passed()).isFalse(); // 650 < 700
        assertThat(service.check(req, null, 600).passed()).isTrue();  // 650 >= 600
    }

    @Test
    @DisplayName("UNAVAILABLE fails when the product mandates the check")
    void unavailable_mandatory_fails() {
        UUID productId = UUID.randomUUID();
        when(provider.pull(any())).thenReturn(CreditReport.unavailable("bureau down"));
        when(mappingRepo.findByLoanProductId(productId)).thenReturn(List.of(mapping(true, true)));

        CreditBureauCheckService.CreditCheckResult r = service.check(req, productId, null);

        assertThat(r.mandatory()).isTrue();
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("UNAVAILABLE passes when the check is not mandatory")
    void unavailable_notMandatory_passes() {
        UUID productId = UUID.randomUUID();
        when(provider.pull(any())).thenReturn(CreditReport.unavailable("bureau down"));
        when(mappingRepo.findByLoanProductId(productId)).thenReturn(List.of(mapping(true, false)));

        CreditBureauCheckService.CreditCheckResult r = service.check(req, productId, null);

        assertThat(r.mandatory()).isFalse();
        assertThat(r.passed()).isTrue();
    }
}

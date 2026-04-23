package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditBureauService — unit tests")
class CreditBureauServiceTest {

    @Mock CreditBureauIntegrationRepository integrationRepo;
    @Mock CreditBureauProductMappingRepository mappingRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks CreditBureauService service;

    private UUID bureauId;
    private CreditBureauIntegration bureau;

    @BeforeEach
    void setUp() {
        bureauId = UUID.randomUUID();
        bureau = new CreditBureauIntegration();
        bureau.setId(bureauId);
        bureau.setName("TransUnion");
        bureau.setImplClass("com.cba.bureau.TransUnionAdapter");
        bureau.setCreditBureauId("TU-001");
        bureau.setCountry("KE");
        bureau.setActive(true);
    }

    @Nested
    @DisplayName("Integrations")
    class Integrations {

        @Test
        @DisplayName("listIntegrations returns page")
        void listIntegrations_returnsPage() {
            when(integrationRepo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bureau)));
            assertThat(service.listIntegrations(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getIntegration returns bureau when found")
        void getIntegration_found() {
            when(integrationRepo.findById(bureauId)).thenReturn(Optional.of(bureau));
            assertThat(service.getIntegration(bureauId).getName()).isEqualTo("TransUnion");
        }

        @Test
        @DisplayName("getIntegration throws when not found")
        void getIntegration_notFound_throws() {
            when(integrationRepo.findById(bureauId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getIntegration(bureauId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createIntegration saves bureau")
        void createIntegration_success() {
            when(integrationRepo.save(any())).thenReturn(bureau);

            CreditBureauService.CreateIntegrationRequest req =
                new CreditBureauService.CreateIntegrationRequest(
                    "TransUnion", "com.cba.bureau.TransUnionAdapter", "TU-001", "KE"
                );
            CreditBureauIntegration result = service.createIntegration(req);
            assertThat(result.getName()).isEqualTo("TransUnion");
        }

        @Test
        @DisplayName("updateIntegration saves changes")
        void updateIntegration_success() {
            when(integrationRepo.findById(bureauId)).thenReturn(Optional.of(bureau));
            when(integrationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreditBureauService.CreateIntegrationRequest req =
                new CreditBureauService.CreateIntegrationRequest(
                    "Metropol", "com.cba.bureau.MetropolAdapter", "MP-001", "KE"
                );
            CreditBureauIntegration result = service.updateIntegration(bureauId, req);
            assertThat(result.getName()).isEqualTo("Metropol");
        }

        @Test
        @DisplayName("activate sets active=true")
        void activate_success() {
            bureau.setActive(false);
            when(integrationRepo.findById(bureauId)).thenReturn(Optional.of(bureau));
            when(integrationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreditBureauIntegration result = service.activate(bureauId);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("deactivate sets active=false")
        void deactivate_success() {
            when(integrationRepo.findById(bureauId)).thenReturn(Optional.of(bureau));
            when(integrationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreditBureauIntegration result = service.deactivate(bureauId);
            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("deleteIntegration removes bureau")
        void deleteIntegration_success() {
            when(integrationRepo.findById(bureauId)).thenReturn(Optional.of(bureau));

            assertThatCode(() -> service.deleteIntegration(bureauId)).doesNotThrowAnyException();
            verify(integrationRepo).delete(bureau);
        }
    }

    @Nested
    @DisplayName("Mappings")
    class Mappings {

        @Test
        @DisplayName("listMappings returns mappings for bureau")
        void listMappings_success() {
            when(mappingRepo.findByCreditBureauId(bureauId)).thenReturn(List.of());
            assertThat(service.listMappings(bureauId)).isEmpty();
        }

        @Test
        @DisplayName("createMapping saves mapping linked to bureau")
        void createMapping_success() {
            UUID loanProductId = UUID.randomUUID();
            CreditBureauProductMapping mapping = new CreditBureauProductMapping();
            mapping.setId(UUID.randomUUID());

            when(integrationRepo.findById(bureauId)).thenReturn(Optional.of(bureau));
            when(mappingRepo.save(any())).thenReturn(mapping);

            CreditBureauService.CreateMappingRequest req =
                new CreditBureauService.CreateMappingRequest(loanProductId, true);
            CreditBureauProductMapping result = service.createMapping(bureauId, req);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("deleteMapping removes mapping")
        void deleteMapping_success() {
            UUID mappingId = UUID.randomUUID();
            CreditBureauProductMapping mapping = new CreditBureauProductMapping();
            mapping.setId(mappingId);

            when(mappingRepo.findById(mappingId)).thenReturn(Optional.of(mapping));

            assertThatCode(() -> service.deleteMapping(mappingId)).doesNotThrowAnyException();
            verify(mappingRepo).delete(mapping);
        }

        @Test
        @DisplayName("deleteMapping throws when not found")
        void deleteMapping_notFound_throws() {
            UUID mappingId = UUID.randomUUID();
            when(mappingRepo.findById(mappingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteMapping(mappingId))
                .isInstanceOf(CbaException.class);
        }
    }
}

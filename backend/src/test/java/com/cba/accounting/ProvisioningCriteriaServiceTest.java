package com.cba.accounting;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProvisioningCriteriaService — unit tests")
class ProvisioningCriteriaServiceTest {

    @Mock ProvisioningCriteriaRepository criteriaRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks ProvisioningCriteriaService service;

    private UUID criteriaId;
    private ProvisioningCriteria criteria;

    @BeforeEach
    void setUp() {
        criteriaId = UUID.randomUUID();
        criteria = new ProvisioningCriteria();
        criteria.setId(criteriaId);
        criteria.setCriteriaName("Standard Provisioning");
        criteria.setActive(true);
        criteria.setDefinitions(new ArrayList<>());
    }

    @Nested
    @DisplayName("List and Get")
    class ListAndGet {

        @Test
        @DisplayName("listCriteria returns page")
        void listCriteria_returnsPage() {
            when(criteriaRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(criteria)));

            assertThat(service.listCriteria(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getCriteria returns criteria when found")
        void getCriteria_found() {
            when(criteriaRepository.findById(criteriaId)).thenReturn(Optional.of(criteria));
            assertThat(service.getCriteria(criteriaId).getCriteriaName()).isEqualTo("Standard Provisioning");
        }

        @Test
        @DisplayName("getCriteria throws when not found")
        void getCriteria_notFound_throws() {
            when(criteriaRepository.findById(criteriaId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getCriteria(criteriaId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("createCriteria saves with definitions")
        void createCriteria_withDefinitions() {
            when(criteriaRepository.save(any())).thenReturn(criteria);

            ProvisioningCriteriaService.DefinitionRequest def =
                new ProvisioningCriteriaService.DefinitionRequest(
                    "STANDARD", 0, 30, new BigDecimal("1.00"),
                    UUID.randomUUID(), UUID.randomUUID()
                );
            ProvisioningCriteriaService.CreateCriteriaRequest req =
                new ProvisioningCriteriaService.CreateCriteriaRequest(
                    "Standard Provisioning", true, List.of(def)
                );

            ProvisioningCriteria result = service.createCriteria(req);
            assertThat(result.getCriteriaName()).isEqualTo("Standard Provisioning");
            verify(criteriaRepository).save(any(ProvisioningCriteria.class));
        }

        @Test
        @DisplayName("createCriteria works with null definitions list")
        void createCriteria_nullDefinitions() {
            when(criteriaRepository.save(any())).thenReturn(criteria);

            ProvisioningCriteriaService.CreateCriteriaRequest req =
                new ProvisioningCriteriaService.CreateCriteriaRequest("Minimal", false, null);

            assertThatCode(() -> service.createCriteria(req)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Update and Delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("updateCriteria clears and replaces definitions")
        void updateCriteria_replacesDefinitions() {
            when(criteriaRepository.findById(criteriaId)).thenReturn(Optional.of(criteria));
            when(criteriaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProvisioningCriteriaService.CreateCriteriaRequest req =
                new ProvisioningCriteriaService.CreateCriteriaRequest("Updated", true, List.of());

            ProvisioningCriteria result = service.updateCriteria(criteriaId, req);
            assertThat(result.getCriteriaName()).isEqualTo("Updated");
        }

        @Test
        @DisplayName("deleteCriteria removes the criteria")
        void deleteCriteria_success() {
            when(criteriaRepository.findById(criteriaId)).thenReturn(Optional.of(criteria));

            assertThatCode(() -> service.deleteCriteria(criteriaId)).doesNotThrowAnyException();
            verify(criteriaRepository).delete(criteria);
        }
    }
}

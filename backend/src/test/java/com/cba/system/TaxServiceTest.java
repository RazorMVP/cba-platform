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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaxService — unit tests")
class TaxServiceTest {

    @Mock TaxComponentRepository componentRepository;
    @Mock TaxGroupRepository groupRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks TaxService taxService;

    private UUID componentId;
    private UUID groupId;
    private TaxComponent component;
    private TaxGroup taxGroup;

    @BeforeEach
    void setUp() {
        componentId = UUID.randomUUID();
        groupId = UUID.randomUUID();

        component = new TaxComponent();
        component.setId(componentId);
        component.setName("VAT 16%");
        component.setPercentage(new BigDecimal("16.00"));
        component.setStartDate(LocalDate.now());

        taxGroup = new TaxGroup();
        taxGroup.setId(groupId);
        taxGroup.setName("Standard Tax Group");
        taxGroup.setStartDate(LocalDate.now());
        taxGroup.setTaxComponents(new ArrayList<>());
    }

    @Nested
    @DisplayName("Tax Components")
    class TaxComponents {

        @Test
        @DisplayName("listComponents returns page of components")
        void listComponents_returnsPage() {
            when(componentRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(component)));

            assertThat(taxService.listComponents(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getComponent returns component when found")
        void getComponent_found() {
            when(componentRepository.findById(componentId)).thenReturn(Optional.of(component));
            assertThat(taxService.getComponent(componentId).getName()).isEqualTo("VAT 16%");
        }

        @Test
        @DisplayName("getComponent throws when not found")
        void getComponent_notFound_throws() {
            when(componentRepository.findById(componentId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> taxService.getComponent(componentId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createComponent saves when name is unique")
        void createComponent_success() {
            when(componentRepository.existsByName("VAT 16%")).thenReturn(false);
            when(componentRepository.save(any())).thenReturn(component);

            TaxService.CreateTaxComponentRequest req = new TaxService.CreateTaxComponentRequest(
                "VAT 16%", new BigDecimal("16.00"), null, null, null, null, LocalDate.now()
            );
            assertThat(taxService.createComponent(req)).isNotNull();
        }

        @Test
        @DisplayName("createComponent throws when name already exists")
        void createComponent_duplicateName_throws() {
            when(componentRepository.existsByName("VAT 16%")).thenReturn(true);

            TaxService.CreateTaxComponentRequest req = new TaxService.CreateTaxComponentRequest(
                "VAT 16%", new BigDecimal("16.00"), null, null, null, null, LocalDate.now()
            );
            assertThatThrownBy(() -> taxService.createComponent(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("updateComponent updates when same name or unique new name")
        void updateComponent_success_sameName() {
            when(componentRepository.findById(componentId)).thenReturn(Optional.of(component));
            when(componentRepository.save(any())).thenReturn(component);

            TaxService.CreateTaxComponentRequest req = new TaxService.CreateTaxComponentRequest(
                "VAT 16%", new BigDecimal("16.00"), null, null, null, null, LocalDate.now()
            );
            assertThat(taxService.updateComponent(componentId, req)).isNotNull();
        }

        @Test
        @DisplayName("updateComponent throws when new name conflicts")
        void updateComponent_nameConflict_throws() {
            when(componentRepository.findById(componentId)).thenReturn(Optional.of(component));
            when(componentRepository.existsByName("WHT 5%")).thenReturn(true);

            TaxService.CreateTaxComponentRequest req = new TaxService.CreateTaxComponentRequest(
                "WHT 5%", new BigDecimal("5.00"), null, null, null, null, LocalDate.now()
            );
            assertThatThrownBy(() -> taxService.updateComponent(componentId, req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("deleteComponent removes component")
        void deleteComponent_success() {
            when(componentRepository.findById(componentId)).thenReturn(Optional.of(component));

            assertThatCode(() -> taxService.deleteComponent(componentId)).doesNotThrowAnyException();
            verify(componentRepository).delete(component);
        }
    }

    @Nested
    @DisplayName("Tax Groups")
    class TaxGroups {

        @Test
        @DisplayName("listGroups returns page of groups")
        void listGroups_returnsPage() {
            when(groupRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(taxGroup)));

            assertThat(taxService.listGroups(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getGroup returns group when found")
        void getGroup_found() {
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(taxGroup));
            assertThat(taxService.getGroup(groupId).getName()).isEqualTo("Standard Tax Group");
        }

        @Test
        @DisplayName("getGroup throws when not found")
        void getGroup_notFound_throws() {
            when(groupRepository.findById(groupId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> taxService.getGroup(groupId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createGroup saves when name is unique and no components")
        void createGroup_noComponents_success() {
            when(groupRepository.existsByName("Standard Tax Group")).thenReturn(false);
            when(groupRepository.save(any())).thenReturn(taxGroup);

            TaxService.CreateTaxGroupRequest req = new TaxService.CreateTaxGroupRequest(
                "Standard Tax Group", LocalDate.now(), null
            );
            assertThat(taxService.createGroup(req)).isNotNull();
        }

        @Test
        @DisplayName("createGroup with component IDs looks up each component")
        void createGroup_withComponents_success() {
            when(groupRepository.existsByName("Full Tax Group")).thenReturn(false);
            when(componentRepository.findById(componentId)).thenReturn(Optional.of(component));
            when(groupRepository.save(any())).thenReturn(taxGroup);

            TaxService.CreateTaxGroupRequest req = new TaxService.CreateTaxGroupRequest(
                "Full Tax Group", LocalDate.now(), List.of(componentId)
            );
            assertThat(taxService.createGroup(req)).isNotNull();
            verify(componentRepository).findById(componentId);
        }

        @Test
        @DisplayName("createGroup throws when name already exists")
        void createGroup_duplicateName_throws() {
            when(groupRepository.existsByName("Standard Tax Group")).thenReturn(true);

            TaxService.CreateTaxGroupRequest req = new TaxService.CreateTaxGroupRequest(
                "Standard Tax Group", LocalDate.now(), null
            );
            assertThatThrownBy(() -> taxService.createGroup(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("updateGroup replaces component set")
        void updateGroup_success() {
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(taxGroup));
            when(groupRepository.save(any())).thenReturn(taxGroup);

            TaxService.CreateTaxGroupRequest req = new TaxService.CreateTaxGroupRequest(
                "Standard Tax Group", LocalDate.now(), null
            );
            assertThat(taxService.updateGroup(groupId, req)).isNotNull();
        }

        @Test
        @DisplayName("deleteGroup removes group")
        void deleteGroup_success() {
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(taxGroup));

            assertThatCode(() -> taxService.deleteGroup(groupId)).doesNotThrowAnyException();
            verify(groupRepository).delete(taxGroup);
        }
    }
}

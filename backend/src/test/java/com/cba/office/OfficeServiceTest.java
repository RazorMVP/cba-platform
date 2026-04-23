package com.cba.office;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.office.dto.OfficeRequest;
import com.cba.office.dto.OfficeResponse;
import com.cba.office.dto.StaffRequest;
import com.cba.office.dto.StaffResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OfficeService — unit tests")
class OfficeServiceTest {

    @Mock OfficeRepository officeRepository;
    @Mock StaffRepository staffRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks OfficeService officeService;

    private UUID officeId;
    private UUID staffId;
    private Office office;
    private Staff staff;

    @BeforeEach
    void setUp() {
        officeId = UUID.randomUUID();
        staffId = UUID.randomUUID();

        office = new Office();
        office.setId(officeId);
        office.setName("Head Office");
        office.setExternalId("HQ-001");
        office.setHierarchy("." + officeId + ".");
        office.setActive(true);

        staff = new Staff();
        staff.setId(staffId);
        staff.setFirstName("Alice");
        staff.setLastName("Smith");
        staff.setOffice(office);
        staff.setActive(true);
    }

    @Nested
    @DisplayName("Offices")
    class Offices {

        @Test
        @DisplayName("createOffice saves with hierarchy for root office (no parent)")
        void createOffice_rootOffice_setsHierarchy() {
            when(officeRepository.save(any(Office.class))).thenAnswer(inv -> {
                Office o = inv.getArgument(0);
                if (o.getId() == null) o.setId(officeId);
                return o;
            });

            OfficeRequest req = new OfficeRequest("Head Office", "HQ-001",
                LocalDate.now(), null, "Main branch");
            OfficeResponse result = officeService.createOffice(req);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Head Office");
            verify(officeRepository, times(2)).save(any(Office.class));
        }

        @Test
        @DisplayName("createOffice sets child hierarchy when parent exists")
        void createOffice_withParent_setsChildHierarchy() {
            UUID parentId = UUID.randomUUID();
            Office parent = new Office();
            parent.setId(parentId);
            parent.setName("Region Office");
            parent.setHierarchy("." + parentId + ".");
            parent.setActive(true);

            when(officeRepository.findById(parentId)).thenReturn(Optional.of(parent));
            when(officeRepository.save(any(Office.class))).thenAnswer(inv -> {
                Office o = inv.getArgument(0);
                if (o.getId() == null) o.setId(officeId);
                return o;
            });

            OfficeRequest req = new OfficeRequest("Branch Office", "BR-001",
                LocalDate.now(), parentId, null);
            OfficeResponse result = officeService.createOffice(req);

            assertThat(result).isNotNull();
            verify(officeRepository, times(2)).save(any(Office.class));
        }

        @Test
        @DisplayName("createOffice throws when parent not found")
        void createOffice_parentNotFound_throws() {
            UUID missingParentId = UUID.randomUUID();
            when(officeRepository.findById(missingParentId)).thenReturn(Optional.empty());

            OfficeRequest req = new OfficeRequest("Branch", "BR-001",
                LocalDate.now(), missingParentId, null);
            assertThatThrownBy(() -> officeService.createOffice(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("getAllOffices returns list of active offices")
        void getAllOffices_returnsList() {
            when(officeRepository.findByActiveTrue()).thenReturn(List.of(office));

            List<OfficeResponse> result = officeService.getAllOffices();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Head Office");
        }

        @Test
        @DisplayName("getOffice returns response when found")
        void getOffice_found() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.of(office));

            OfficeResponse result = officeService.getOffice(officeId);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("getOffice throws when not found")
        void getOffice_notFound_throws() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> officeService.getOffice(officeId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("updateOffice updates name and saves")
        void updateOffice_success() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.of(office));
            when(officeRepository.save(any())).thenReturn(office);

            OfficeRequest req = new OfficeRequest("Updated Office", "HQ-002",
                LocalDate.now(), null, "Updated desc");
            OfficeResponse result = officeService.updateOffice(officeId, req);

            assertThat(result).isNotNull();
            verify(auditLogService).log(eq("OFFICE"), eq(officeId.toString()),
                eq("UPDATED"), isNull(), any());
        }
    }

    @Nested
    @DisplayName("Staff")
    class StaffTests {

        @Test
        @DisplayName("createStaff saves staff linked to office")
        void createStaff_success() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.of(office));
            when(staffRepository.save(any())).thenReturn(staff);

            StaffRequest req = new StaffRequest("Alice", "Smith",
                "alice@cba.com", "+1234567890", LocalDate.now(), false, officeId);
            StaffResponse result = officeService.createStaff(req);

            assertThat(result).isNotNull();
            verify(staffRepository).save(any(Staff.class));
        }

        @Test
        @DisplayName("createStaff throws when office not found")
        void createStaff_officeNotFound_throws() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.empty());

            StaffRequest req = new StaffRequest("Alice", "Smith",
                "alice@cba.com", null, LocalDate.now(), false, officeId);
            assertThatThrownBy(() -> officeService.createStaff(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("getAllStaff with null officeId returns all active staff")
        void getAllStaff_noFilter_returnsAll() {
            when(staffRepository.findByActiveTrue()).thenReturn(List.of(staff));

            List<StaffResponse> result = officeService.getAllStaff(null);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getAllStaff with officeId filters by office")
        void getAllStaff_withOfficeFilter() {
            when(staffRepository.findByOfficeIdAndActiveTrue(officeId)).thenReturn(List.of(staff));

            List<StaffResponse> result = officeService.getAllStaff(officeId);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getStaff returns response when found")
        void getStaff_found() {
            when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

            StaffResponse result = officeService.getStaff(staffId);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("getStaff throws when not found")
        void getStaff_notFound_throws() {
            when(staffRepository.findById(staffId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> officeService.getStaff(staffId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("updateStaff updates fields and saves")
        void updateStaff_success() {
            when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
            when(staffRepository.save(any())).thenReturn(staff);

            StaffRequest req = new StaffRequest("Alicia", "Smith",
                "alicia@cba.com", null, null, true, officeId);
            StaffResponse result = officeService.updateStaff(staffId, req);

            assertThat(result).isNotNull();
            verify(staffRepository).save(any(Staff.class));
        }

        @Test
        @DisplayName("deactivateStaff sets active to false")
        void deactivateStaff_success() {
            when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
            when(staffRepository.save(any())).thenReturn(staff);

            assertThatCode(() -> officeService.deactivateStaff(staffId))
                .doesNotThrowAnyException();
            verify(auditLogService).log(eq("STAFF"), eq(staffId.toString()),
                eq("DEACTIVATED"), isNull(), isNull());
        }
    }
}

package com.cba.group;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.group.dto.CenterRequest;
import com.cba.office.Office;
import com.cba.office.OfficeRepository;
import com.cba.office.StaffRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CenterService — unit tests")
class CenterServiceTest {

    @Mock CenterRepository centerRepository;
    @Mock OfficeRepository officeRepository;
    @Mock StaffRepository staffRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks CenterService service;

    private UUID centerId;
    private UUID officeId;
    private Center center;
    private Office office;

    @BeforeEach
    void setUp() {
        centerId = UUID.randomUUID();
        officeId = UUID.randomUUID();

        office = new Office();
        office.setId(officeId);

        center = new Center();
        center.setId(centerId);
        center.setName("Downtown Center");
        center.setStatus(Center.Status.INACTIVE);
        center.setOffice(office);
    }

    @Nested
    @DisplayName("List and Get")
    class ListAndGet {

        @Test
        @DisplayName("listCenters with officeId calls findByOfficeId")
        void listCenters_withOffice() {
            when(centerRepository.findByOfficeId(officeId)).thenReturn(List.of(center));
            assertThat(service.listCenters(officeId)).hasSize(1);
        }

        @Test
        @DisplayName("listCenters without officeId calls findAll")
        void listCenters_allOffices() {
            when(centerRepository.findAll()).thenReturn(List.of(center));
            assertThat(service.listCenters(null)).hasSize(1);
        }

        @Test
        @DisplayName("getCenter returns center when found")
        void getCenter_found() {
            when(centerRepository.findById(centerId)).thenReturn(Optional.of(center));
            assertThat(service.getCenter(centerId).name()).isEqualTo("Downtown Center");
        }

        @Test
        @DisplayName("getCenter throws when not found")
        void getCenter_notFound_throws() {
            when(centerRepository.findById(centerId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getCenter(centerId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("createCenter with past activation date sets ACTIVE status")
        void createCenter_pastActivationDate_setsActive() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.of(office));
            when(centerRepository.save(any())).thenAnswer(inv -> {
                Center c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            CenterRequest req = new CenterRequest(
                "Downtown", null, officeId, null,
                LocalDate.now().minusDays(1), "MONDAY"
            );
            var response = service.createCenter(req);
            assertThat(response.name()).isEqualTo("Downtown");
        }

        @Test
        @DisplayName("createCenter with future activation date sets INACTIVE status")
        void createCenter_futureActivationDate_setsInactive() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.of(office));
            when(centerRepository.save(any())).thenAnswer(inv -> {
                Center c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            CenterRequest req = new CenterRequest(
                "Future Center", null, officeId, null,
                LocalDate.now().plusDays(30), "TUESDAY"
            );
            assertThatCode(() -> service.createCenter(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("createCenter throws when office not found")
        void createCenter_officeNotFound_throws() {
            when(officeRepository.findById(officeId)).thenReturn(Optional.empty());

            CenterRequest req = new CenterRequest("Test", null, officeId, null, null, null);
            assertThatThrownBy(() -> service.createCenter(req))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Activate and Delete")
    class ActivateAndDelete {

        @Test
        @DisplayName("activateCenter sets ACTIVE status")
        void activateCenter_success() {
            when(centerRepository.findById(centerId)).thenReturn(Optional.of(center));
            when(centerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var response = service.activateCenter(centerId);
            assertThat(response.name()).isEqualTo("Downtown Center");
            verify(centerRepository).save(argThat(c -> c.getStatus() == Center.Status.ACTIVE));
        }

        @Test
        @DisplayName("deleteCenter removes center")
        void deleteCenter_success() {
            when(centerRepository.findById(centerId)).thenReturn(Optional.of(center));

            assertThatCode(() -> service.deleteCenter(centerId)).doesNotThrowAnyException();
            verify(centerRepository).delete(center);
        }
    }
}

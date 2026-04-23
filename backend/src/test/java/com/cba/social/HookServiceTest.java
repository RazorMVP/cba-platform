package com.cba.social;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HookService — unit tests")
class HookServiceTest {

    @Mock HookRepository hookRepository;
    @Mock HolidayRepository holidayRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks HookService service;

    private UUID hookId;
    private UUID holidayId;
    private Hook hook;
    private Holiday holiday;

    @BeforeEach
    void setUp() {
        hookId = UUID.randomUUID();
        holidayId = UUID.randomUUID();

        hook = new Hook();
        hook.setId(hookId);
        hook.setName("Loan Hook");
        hook.setHookType(Hook.HookType.WEB);
        hook.setPayloadUrl("https://example.com/webhook");

        holiday = new Holiday();
        holiday.setId(holidayId);
        holiday.setName("Christmas");
        holiday.setStatus(Holiday.Status.PENDING);
    }

    @Nested
    @DisplayName("Hooks")
    class Hooks {

        @Test
        @DisplayName("listHooks returns page")
        void listHooks_returnsPage() {
            when(hookRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(hook)));
            assertThat(service.listHooks(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getHook returns hook when found")
        void getHook_found() {
            when(hookRepository.findById(hookId)).thenReturn(Optional.of(hook));
            assertThat(service.getHook(hookId).getName()).isEqualTo("Loan Hook");
        }

        @Test
        @DisplayName("getHook throws when not found")
        void getHook_notFound_throws() {
            when(hookRepository.findById(hookId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getHook(hookId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createHook defaults hookType to WEB and contentType to application/json")
        void createHook_defaults() {
            when(hookRepository.save(any())).thenAnswer(inv -> {
                Hook h = inv.getArgument(0);
                h.setId(UUID.randomUUID());
                return h;
            });

            HookService.CreateHookRequest req = new HookService.CreateHookRequest(
                "Test Hook", null, "https://example.com", null, null,
                List.of("LOAN_APPROVED"), true
            );
            Hook result = service.createHook(req);
            assertThat(result.getHookType()).isEqualTo(Hook.HookType.WEB);
            assertThat(result.getContentType()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("updateHook saves changes")
        void updateHook_success() {
            when(hookRepository.findById(hookId)).thenReturn(Optional.of(hook));
            when(hookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            HookService.CreateHookRequest req = new HookService.CreateHookRequest(
                "Updated Hook", Hook.HookType.SMS, "https://new.com", "text/plain", null,
                List.of("LOAN_DISBURSED"), false
            );
            Hook result = service.updateHook(hookId, req);
            assertThat(result.getName()).isEqualTo("Updated Hook");
        }

        @Test
        @DisplayName("deleteHook removes hook")
        void deleteHook_success() {
            when(hookRepository.findById(hookId)).thenReturn(Optional.of(hook));

            assertThatCode(() -> service.deleteHook(hookId)).doesNotThrowAnyException();
            verify(hookRepository).delete(hook);
        }
    }

    @Nested
    @DisplayName("Holidays")
    class Holidays {

        @Test
        @DisplayName("listHolidays returns page")
        void listHolidays_returnsPage() {
            when(holidayRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(holiday)));
            assertThat(service.listHolidays(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getHoliday returns holiday when found")
        void getHoliday_found() {
            when(holidayRepository.findById(holidayId)).thenReturn(Optional.of(holiday));
            assertThat(service.getHoliday(holidayId).getName()).isEqualTo("Christmas");
        }

        @Test
        @DisplayName("createHoliday saves holiday")
        void createHoliday_success() {
            when(holidayRepository.save(any())).thenAnswer(inv -> {
                Holiday h = inv.getArgument(0);
                h.setId(UUID.randomUUID());
                return h;
            });

            HookService.CreateHolidayRequest req = new HookService.CreateHolidayRequest(
                "Christmas", LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 25),
                Holiday.RepaymentSchedulingType.NEXT_WORKING_DAY, null
            );
            Holiday result = service.createHoliday(req);
            assertThat(result.getName()).isEqualTo("Christmas");
        }

        @Test
        @DisplayName("activateHoliday sets ACTIVE status")
        void activateHoliday_success() {
            when(holidayRepository.findById(holidayId)).thenReturn(Optional.of(holiday));
            when(holidayRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Holiday result = service.activateHoliday(holidayId);
            assertThat(result.getStatus()).isEqualTo(Holiday.Status.ACTIVE);
        }

        @Test
        @DisplayName("deleteHoliday removes holiday")
        void deleteHoliday_success() {
            when(holidayRepository.findById(holidayId)).thenReturn(Optional.of(holiday));

            assertThatCode(() -> service.deleteHoliday(holidayId)).doesNotThrowAnyException();
            verify(holidayRepository).delete(holiday);
        }
    }
}

package com.cba.notification;

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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InAppNotificationService — unit tests")
class InAppNotificationServiceTest {

    @Mock InAppNotificationRepository notifRepo;
    @Mock UserNotificationPrefRepository prefRepo;
    @Mock PushDeviceRepository deviceRepo;

    @InjectMocks InAppNotificationService service;

    private String userId;

    @BeforeEach
    void setUp() {
        userId = "user-123";
    }

    @Nested
    @DisplayName("Notifications")
    class Notifications {

        @Test
        @DisplayName("push saves and returns notification")
        void push_success() {
            InAppNotification saved = new InAppNotification();
            saved.setId(UUID.randomUUID());
            when(notifRepo.save(any())).thenReturn(saved);

            InAppNotification result = service.push(
                InAppNotification.Type.LOAN_APPROVED,
                InAppNotification.Severity.INFO,
                "Loan Approved",
                "Your loan was approved",
                "Loan",
                UUID.randomUUID()
            );
            assertThat(result).isNotNull();
            verify(notifRepo).save(any(InAppNotification.class));
        }

        @Test
        @DisplayName("getNotifications returns page")
        void getNotifications_returnsPage() {
            when(notifRepo.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
            assertThat(service.getNotifications(Pageable.unpaged()).getContent()).isEmpty();
        }

        @Test
        @DisplayName("getUnreadCount uses horizon from pref when pref exists")
        void getUnreadCount_withPref() {
            OffsetDateTime horizon = OffsetDateTime.now().minusHours(1);
            UserNotificationPref pref = new UserNotificationPref();
            pref.setUserId(userId);
            pref.setLastReadAt(horizon);
            when(prefRepo.findByUserId(userId)).thenReturn(Optional.of(pref));
            when(notifRepo.countByCreatedAtAfter(horizon)).thenReturn(5L);

            assertThat(service.getUnreadCount(userId)).isEqualTo(5L);
        }

        @Test
        @DisplayName("getUnreadCount uses 7-day fallback when no pref")
        void getUnreadCount_noPref_uses7DayFallback() {
            when(prefRepo.findByUserId(userId)).thenReturn(Optional.empty());
            when(notifRepo.countByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(3L);

            assertThat(service.getUnreadCount(userId)).isEqualTo(3L);
        }

        @Test
        @DisplayName("markAllRead updates existing pref")
        void markAllRead_existingPref() {
            UserNotificationPref pref = new UserNotificationPref();
            pref.setUserId(userId);
            pref.setLastReadAt(OffsetDateTime.now().minusDays(1));
            when(prefRepo.findByUserId(userId)).thenReturn(Optional.of(pref));
            when(prefRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> service.markAllRead(userId)).doesNotThrowAnyException();
            verify(prefRepo).save(argThat(p -> p.getLastReadAt() != null));
        }

        @Test
        @DisplayName("markAllRead creates new pref when none exists")
        void markAllRead_noPref_creates() {
            when(prefRepo.findByUserId(userId)).thenReturn(Optional.empty());
            when(prefRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> service.markAllRead(userId)).doesNotThrowAnyException();
            verify(prefRepo).save(any(UserNotificationPref.class));
        }
    }

    @Nested
    @DisplayName("Push Devices")
    class PushDevices {

        @Test
        @DisplayName("registerDevice saves new device when token not found")
        void registerDevice_newDevice() {
            when(deviceRepo.findByFcmToken("token-abc")).thenReturn(Optional.empty());
            when(deviceRepo.save(any())).thenAnswer(inv -> {
                PushDevice d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

            PushDevice result = service.registerDevice(userId, "token-abc",
                PushDevice.Platform.ANDROID, "My Phone");
            assertThat(result).isNotNull();
            assertThat(result.isActive()).isTrue();
            verify(deviceRepo).save(any(PushDevice.class));
        }

        @Test
        @DisplayName("registerDevice updates existing device when token already exists")
        void registerDevice_existingToken() {
            PushDevice existing = new PushDevice();
            existing.setId(UUID.randomUUID());
            existing.setFcmToken("token-abc");
            when(deviceRepo.findByFcmToken("token-abc")).thenReturn(Optional.of(existing));
            when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PushDevice result = service.registerDevice(userId, "token-abc",
                PushDevice.Platform.IOS, "New Label");
            assertThat(result.getPlatform()).isEqualTo(PushDevice.Platform.IOS);
        }

        @Test
        @DisplayName("deregisterDevice sets active=false when owner matches")
        void deregisterDevice_success() {
            UUID deviceId = UUID.randomUUID();
            PushDevice device = new PushDevice();
            device.setId(deviceId);
            device.setUserId(userId);
            device.setActive(true);
            when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(device));
            when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> service.deregisterDevice(userId, deviceId))
                .doesNotThrowAnyException();
            verify(deviceRepo).save(argThat(d -> !d.isActive()));
        }

        @Test
        @DisplayName("deregisterDevice is no-op when device not found")
        void deregisterDevice_notFound_noOp() {
            UUID deviceId = UUID.randomUUID();
            when(deviceRepo.findById(deviceId)).thenReturn(Optional.empty());

            assertThatCode(() -> service.deregisterDevice(userId, deviceId))
                .doesNotThrowAnyException();
            verify(deviceRepo, never()).save(any());
        }

        @Test
        @DisplayName("deregisterDevice is no-op when userId does not match")
        void deregisterDevice_wrongUser_noOp() {
            UUID deviceId = UUID.randomUUID();
            PushDevice device = new PushDevice();
            device.setId(deviceId);
            device.setUserId("other-user");
            device.setActive(true);
            when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(device));

            assertThatCode(() -> service.deregisterDevice(userId, deviceId))
                .doesNotThrowAnyException();
            verify(deviceRepo, never()).save(any());
        }

        @Test
        @DisplayName("getDevices returns list for user")
        void getDevices_returnsDevices() {
            when(deviceRepo.findByUserId(userId)).thenReturn(List.of());
            assertThat(service.getDevices(userId)).isEmpty();
        }
    }
}

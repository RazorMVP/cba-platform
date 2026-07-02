package com.cba.notification;

import com.cba.notification.push.PushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PushDispatchService — per-user fan-out + dead-token pruning")
class PushDispatchServiceTest {

    @Mock PushSender pushSender;
    @Mock PushDeviceRepository deviceRepo;

    @InjectMocks PushDispatchService service;

    private PushDevice device(String token) {
        PushDevice d = new PushDevice();
        d.setUserId("user1");
        d.setFcmToken(token);
        d.setPlatform(PushDevice.Platform.ANDROID);
        d.setActive(true);
        return d;
    }

    @BeforeEach
    void stubSave() {
        lenient().when(deviceRepo.save(any(PushDevice.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pushSender.providerId()).thenReturn("NONE");
    }

    @Test
    @DisplayName("sends to every active device and counts successes")
    void sendsToAllDevices() {
        when(deviceRepo.findByUserIdAndActiveTrue("user1"))
            .thenReturn(List.of(device("t1"), device("t2")));
        when(pushSender.send(anyString(), any(), any(), any()))
            .thenReturn(PushSender.PushResult.accepted("id"));

        PushDispatchService.PushDispatchResult r =
            service.sendToUser("user1", "Title", "Body", Map.of());

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.sent()).isEqualTo(2);
        assertThat(r.failed()).isZero();
        assertThat(r.deactivated()).isZero();
    }

    @Test
    @DisplayName("a dead token is deactivated and counted")
    void deadTokenDeactivated() {
        PushDevice dead = device("dead");
        when(deviceRepo.findByUserIdAndActiveTrue("user1")).thenReturn(List.of(dead));
        when(pushSender.send(anyString(), any(), any(), any()))
            .thenReturn(PushSender.PushResult.invalidToken("gone"));

        PushDispatchService.PushDispatchResult r =
            service.sendToUser("user1", "T", "B", null);

        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.deactivated()).isEqualTo(1);
        assertThat(dead.isActive()).isFalse();
        verify(deviceRepo).save(dead);
    }

    @Test
    @DisplayName("a plain rejection fails but does not deactivate the device")
    void rejectionKeepsDeviceActive() {
        PushDevice d = device("t");
        when(deviceRepo.findByUserIdAndActiveTrue("user1")).thenReturn(List.of(d));
        when(pushSender.send(anyString(), any(), any(), any()))
            .thenReturn(PushSender.PushResult.rejected("HTTP_500", "boom"));

        PushDispatchService.PushDispatchResult r =
            service.sendToUser("user1", "T", "B", null);

        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.deactivated()).isZero();
        assertThat(d.isActive()).isTrue();
    }

    @Test
    @DisplayName("no devices → all-zero result, provider still reported")
    void noDevices() {
        when(deviceRepo.findByUserIdAndActiveTrue("user1")).thenReturn(List.of());

        PushDispatchService.PushDispatchResult r =
            service.sendToUser("user1", "T", "B", null);

        assertThat(r.total()).isZero();
        assertThat(r.provider()).isEqualTo("NONE");
        verify(pushSender, never()).send(anyString(), any(), any(), any());
    }
}

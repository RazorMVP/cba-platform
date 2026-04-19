package com.cba.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final InAppNotificationRepository notifRepo;
    private final UserNotificationPrefRepository prefRepo;
    private final PushDeviceRepository deviceRepo;

    @Transactional
    public InAppNotification push(InAppNotification.Type type,
                                   InAppNotification.Severity severity,
                                   String title,
                                   String message,
                                   String entityType,
                                   UUID entityId) {
        InAppNotification n = new InAppNotification();
        n.setType(type);
        n.setSeverity(severity);
        n.setTitle(title);
        n.setMessage(message);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        return notifRepo.save(n);
    }

    @Transactional(readOnly = true)
    public Page<InAppNotification> getNotifications(Pageable pageable) {
        return notifRepo.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        OffsetDateTime horizon = prefRepo.findByUserId(userId)
                .map(UserNotificationPref::getLastReadAt)
                .orElse(OffsetDateTime.now().minusDays(7));
        return notifRepo.countByCreatedAtAfter(horizon);
    }

    @Transactional
    public void markAllRead(String userId) {
        UserNotificationPref pref = prefRepo.findByUserId(userId)
                .orElseGet(() -> UserNotificationPref.forUser(userId));
        pref.setLastReadAt(OffsetDateTime.now());
        prefRepo.save(pref);
    }

    // Push device management

    @Transactional
    public PushDevice registerDevice(String userId, String fcmToken,
                                      PushDevice.Platform platform, String deviceLabel) {
        PushDevice device = deviceRepo.findByFcmToken(fcmToken)
                .orElseGet(PushDevice::new);
        device.setUserId(userId);
        device.setFcmToken(fcmToken);
        device.setPlatform(platform);
        device.setDeviceLabel(deviceLabel);
        device.setActive(true);
        device.setLastSeenAt(OffsetDateTime.now());
        return deviceRepo.save(device);
    }

    @Transactional
    public void deregisterDevice(String userId, UUID deviceId) {
        deviceRepo.findById(deviceId).ifPresent(d -> {
            if (d.getUserId().equals(userId)) {
                d.setActive(false);
                deviceRepo.save(d);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<PushDevice> getDevices(String userId) {
        return deviceRepo.findByUserId(userId);
    }
}

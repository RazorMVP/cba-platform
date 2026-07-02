package com.cba.notification;

import com.cba.notification.push.PushSender;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fans a notification out to all of a user's active {@link PushDevice}s through the pluggable
 * {@link PushSender}. Updates {@code lastSeenAt} on delivery and <b>auto-deactivates</b> a
 * device whose token the provider reports as dead — so a rotated/uninstalled app stops being
 * retried.
 */
@Service
@RequiredArgsConstructor
public class PushDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PushDispatchService.class);

    private final PushSender pushSender;
    private final PushDeviceRepository deviceRepo;

    /** Per-user fan-out outcome. */
    public record PushDispatchResult(int total, int sent, int failed, int deactivated, String provider) {}

    @Transactional
    public PushDispatchResult sendToUser(String userId, String title, String body, Map<String, String> data) {
        List<PushDevice> devices = deviceRepo.findByUserIdAndActiveTrue(userId);
        int sent = 0, failed = 0, deactivated = 0;

        for (PushDevice device : devices) {
            PushSender.PushResult r = pushSender.send(device.getFcmToken(), title, body, data);
            if (r.accepted()) {
                sent++;
                device.setLastSeenAt(OffsetDateTime.now());
            } else {
                failed++;
                if (r.tokenInvalid()) {
                    device.setActive(false);
                    deactivated++;
                }
            }
            deviceRepo.save(device);
        }

        if (deactivated > 0) {
            log.info("[PUSH] deactivated {} dead token(s) for user {}", deactivated, userId);
        }
        return new PushDispatchResult(devices.size(), sent, failed, deactivated, pushSender.providerId());
    }
}

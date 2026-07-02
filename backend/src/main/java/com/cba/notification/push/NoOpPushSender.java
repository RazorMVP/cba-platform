package com.cba.notification.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Default push sender — logs and simulates a successful push without contacting Firebase/APNs.
 * Active when {@code app.push.provider} is absent or {@code NONE}. The device token is masked
 * in logs (it is a bearer credential).
 */
@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "NONE", matchIfMissing = true)
public class NoOpPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpPushSender.class);

    @Override
    public PushResult send(String token, String title, String body, Map<String, String> data) {
        String id = "noop-" + UUID.randomUUID();
        log.info("[PUSH:NOOP] simulated push to {} title=\"{}\" id={}", mask(token), title, id);
        return PushResult.accepted(id);
    }

    @Override
    public String providerId() {
        return "NONE";
    }

    /** Show only the last 6 chars of a device token. */
    static String mask(String token) {
        if (token == null || token.length() <= 6) return "******";
        return "****" + token.substring(token.length() - 6);
    }
}

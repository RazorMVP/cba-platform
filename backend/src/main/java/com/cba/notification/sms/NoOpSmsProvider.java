package com.cba.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default SMS provider — logs and simulates a successful submit without contacting any
 * gateway. Active when {@code app.sms.provider} is absent or {@code NONE}.
 *
 * <p>This is the dev/sandbox equivalent of MailHog for email: the campaign lifecycle
 * (message rows → {@code SENT}) is exercised end-to-end with no credentials, and no
 * real SMS is billed. The recipient number is masked in logs per the PII rule.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "NONE", matchIfMissing = true)
public class NoOpSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsProvider.class);

    @Override
    public SmsResult send(String toPhoneNumber, String message) {
        String id = "noop-" + UUID.randomUUID();
        log.info("[SMS:NOOP] simulated send to {} ({} chars), id={}",
                mask(toPhoneNumber), message == null ? 0 : message.length(), id);
        return SmsResult.accepted(id);
    }

    @Override
    public String providerId() {
        return "NONE";
    }

    /** Mask all but the last 4 digits of an MSISDN, e.g. {@code +2547****0000 → ****0000}. */
    static String mask(String phone) {
        if (phone == null || phone.length() <= 4) return "****";
        return "****" + phone.substring(phone.length() - 4);
    }
}

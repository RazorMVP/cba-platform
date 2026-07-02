package com.cba.notification.push;

import java.util.Map;

/**
 * Transport abstraction for a single push notification. Pluggable like the SMS/credit/payment
 * providers: one implementation is active, chosen by {@code app.push.provider}.
 *
 * <p>Default {@link NoOpPushSender} ({@code matchIfMissing=true}) logs and simulates delivery,
 * so dev needs no Firebase/APNs credentials. Set {@code app.push.provider=HTTP} +
 * {@code app.push.http.*} to relay through a real push service ({@link HttpPushSender}); a
 * native FCM v1 / APNs client is a drop-in sibling behind a new {@code havingValue}.
 *
 * <p>The {@code tokenInvalid} flag on {@link PushResult} lets the dispatch layer prune dead
 * tokens (FCM {@code UNREGISTERED} / HTTP 404/410) so a rotated device is not retried forever.
 */
public interface PushSender {

    /** Send to one device token. Never throws for a provider-side failure — returns a rejected result. */
    PushResult send(String token, String title, String body, Map<String, String> data);

    /** Stable id of the active sender, e.g. {@code NONE}, {@code HTTP}. */
    String providerId();

    /**
     * Outcome of a single push. {@code accepted} = the service acknowledged it (not proof of
     * handset receipt). {@code tokenInvalid} = the token is dead and should be deactivated.
     */
    record PushResult(boolean accepted, String messageId, String errorCode, String errorMessage, boolean tokenInvalid) {

        public static PushResult accepted(String messageId) {
            return new PushResult(true, messageId, null, null, false);
        }

        public static PushResult rejected(String errorCode, String errorMessage) {
            return new PushResult(false, null, errorCode, errorMessage, false);
        }

        public static PushResult invalidToken(String errorMessage) {
            return new PushResult(false, null, "TOKEN_INVALID", errorMessage, true);
        }
    }
}

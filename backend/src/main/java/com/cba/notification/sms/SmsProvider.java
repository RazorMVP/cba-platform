package com.cba.notification.sms;

/**
 * Transport abstraction for outbound SMS.
 *
 * <p>Pluggable like {@link com.cba.customer.storage.StorageProvider}: exactly one
 * implementation is active at runtime, selected by {@code app.sms.provider}. The
 * default ({@link NoOpSmsProvider}, {@code matchIfMissing=true}) keeps dev/test
 * running with no gateway credentials; flip {@code app.sms.provider=HTTP} and supply
 * {@code app.sms.http.*} to dispatch through a real gateway ({@link HttpSmsProvider}).
 *
 * <p>This interface deliberately carries no JPA/entity coupling so it can be reused
 * by any sender (campaigns, 2FA OTP, transactional alerts). Persisting a delivery
 * record is the caller's job (see {@code SmsDispatchService}).
 */
public interface SmsProvider {

    /**
     * Submit a single message to the gateway. Implementations must never throw for a
     * gateway-side rejection — return {@link SmsResult#rejected} instead. They may
     * throw only for programming errors (null arguments).
     *
     * @param toPhoneNumber recipient MSISDN (E.164 preferred, e.g. {@code +254700000000})
     * @param message       message body
     * @return accepted (with the gateway's message id) or rejected (with an error code)
     */
    SmsResult send(String toPhoneNumber, String message);

    /** Stable identifier of the active provider, e.g. {@code NONE}, {@code HTTP}. */
    String providerId();

    /**
     * Outcome of a submit attempt. {@code accepted} means the gateway acknowledged the
     * message for delivery — not that it reached the handset (delivery receipts are a
     * separate, asynchronous concern not modelled here).
     */
    record SmsResult(boolean accepted, String providerMessageId, String errorCode, String errorMessage) {

        public static SmsResult accepted(String providerMessageId) {
            return new SmsResult(true, providerMessageId, null, null);
        }

        public static SmsResult rejected(String errorCode, String errorMessage) {
            return new SmsResult(false, null, errorCode, errorMessage);
        }
    }
}

package com.cba.payment.gateway;

/**
 * Synchronous answer from an external payments gateway to a submit. It is the <em>submit
 * ack</em>, not settlement: real SWIFT/SEPA/ACH settlement is asynchronous and arrives later
 * through the signed callback ({@code POST /api/v1/payments/external/callbacks}).
 *
 * <ul>
 *   <li>{@code ACCEPTED} — the gateway took the instruction. {@code settled} only when the
 *       gateway settles at once (the simulated one, in dev).</li>
 *   <li>{@code REJECTED} — a definite refusal (4xx, or no gateway configured): the payment
 *       service throws and the debit rolls back.</li>
 *   <li>{@code UNKNOWN} — timeout, transport error or 5xx: the instruction may have gone out.
 *       The debit is kept and the payment waits in PROCESSING for the callback or ops
 *       (rolling back could let money leave the bank with no debit).</li>
 * </ul>
 */
public record GatewayResult(Status status, String networkReference, String errorCode, String errorMessage,
                            boolean settled) {

    public enum Status { ACCEPTED, REJECTED, UNKNOWN }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }

    public static GatewayResult accepted(String networkReference) {
        return new GatewayResult(Status.ACCEPTED, networkReference, null, null, false);
    }

    /** Accepted and already settled (no callback will follow). */
    public static GatewayResult settled(String networkReference) {
        return new GatewayResult(Status.ACCEPTED, networkReference, null, null, true);
    }

    public static GatewayResult rejected(String errorCode, String errorMessage) {
        return new GatewayResult(Status.REJECTED, null, errorCode, errorMessage, false);
    }

    public static GatewayResult unknown(String errorCode, String errorMessage) {
        return new GatewayResult(Status.UNKNOWN, null, errorCode, errorMessage, false);
    }
}

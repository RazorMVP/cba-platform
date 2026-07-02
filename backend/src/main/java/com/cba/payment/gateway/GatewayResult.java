package com.cba.payment.gateway;

/**
 * Synchronous acknowledgement from an external payments gateway. This models the
 * <em>submit ack</em> (accepted-for-processing), not final settlement — real SWIFT/SEPA
 * settlement is asynchronous and would arrive later via a status webhook.
 *
 * <p>A {@code REJECTED} result must abort the transfer: the payment service throws, which
 * rolls back the (not-yet-committed) debit — no phantom debit is ever left behind.
 */
public record GatewayResult(Status status, String networkReference, String errorCode, String errorMessage) {

    public enum Status { ACCEPTED, REJECTED }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }

    public static GatewayResult accepted(String networkReference) {
        return new GatewayResult(Status.ACCEPTED, networkReference, null, null);
    }

    public static GatewayResult rejected(String errorCode, String errorMessage) {
        return new GatewayResult(Status.REJECTED, null, errorCode, errorMessage);
    }
}

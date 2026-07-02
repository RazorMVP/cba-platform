package com.cba.payment.gateway;

/**
 * Transport abstraction for submitting a cross-border / interbank payment to an external
 * network (SWIFT, SEPA, ACH). Pluggable like the SMS and credit-bureau providers: one
 * implementation is active, chosen by {@code app.payments.external.gateway}.
 *
 * <p>Default {@link SimulatedExternalPaymentGateway} ({@code matchIfMissing=true}) accepts
 * every instruction and returns a synthetic network reference — preserving the current
 * "external payment completes immediately" dev behaviour with no bank connectivity. Set
 * {@code app.payments.external.gateway=HTTP} + {@code app.payments.external.http.*} to
 * submit through a real gateway ({@link HttpExternalPaymentGateway}).
 *
 * <p>Unlike the SMS/bureau providers, a gateway failure is <em>not</em> silently tolerated:
 * a {@code REJECTED}/errored submit causes {@code PaymentService} to abort and roll back the
 * debit — money must never leave an account for a payment the network refused.
 */
public interface ExternalPaymentGateway {

    /** Submit an instruction. Implementations return {@link GatewayResult#rejected} on any failure, never throw. */
    GatewayResult submit(ExternalPaymentInstruction instruction);

    /** Stable id of the active gateway, e.g. {@code SIMULATED}, {@code HTTP}. */
    String gatewayId();
}

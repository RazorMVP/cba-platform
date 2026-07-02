package com.cba.system.bureau;

/**
 * Transport abstraction for a credit-bureau pull. Pluggable exactly like
 * {@link com.cba.notification.sms.SmsProvider}: one implementation is active at runtime,
 * chosen by {@code app.creditbureau.provider}.
 *
 * <p>Default {@link SimulatedCreditBureauProvider} ({@code matchIfMissing=true}) returns a
 * deterministic score with no external call, so dev/test needs no bureau contract. Set
 * {@code app.creditbureau.provider=HTTP} + {@code app.creditbureau.http.*} to pull from a
 * real bureau ({@link HttpCreditBureauProvider}).
 *
 * <p>The existing {@code CreditBureauIntegration.implClass} column is descriptive metadata
 * (which bureau brand an admin registered); the <em>active adapter</em> is selected by
 * Spring config, mirroring the storage/SMS providers — no reflective class loading.
 */
public interface CreditBureauProvider {

    /**
     * Pull a report. Implementations must never throw for a bureau-side failure — return
     * {@link CreditReport#unavailable} instead, so a flaky bureau never destabilises the
     * loan-origination flow.
     */
    CreditReport pull(CreditCheckRequest request);

    /** Stable id of the active provider, e.g. {@code SIMULATED}, {@code HTTP}. */
    String providerId();
}

package com.cba.card.interchange;

/**
 * Types of scheme fees charged by card networks on every transaction.
 *
 * <p>These are separate from interchange (which flows issuer←→acquirer) and
 * are paid by both issuer and acquirer to the scheme.
 */
public enum SchemeFeeType {
    /** Basis-point assessment on gross transaction volume (e.g. Visa 0.11%). */
    ASSESSMENT,
    /** Per-transaction network processing fee (e.g. Visa APF $0.0195). */
    NETWORK,
    /** Additional percentage levied when transaction crosses country borders. */
    CROSS_BORDER,
    /** International Service Assessment — applied when transaction currency differs from card billing currency. */
    INTERNATIONAL_SERVICE
}

package com.cba.card.bureau;

/**
 * Lifecycle states for a card personalization bureau job (batch).
 *
 * <pre>
 * PENDING → SENT → CONFIRMED
 *        ↘          ↘
 *         FAILED      FAILED (production error reported by bureau)
 * </pre>
 */
public enum BureauJobStatus {

    /** Batch created locally; CDP file not yet transmitted to the bureau. */
    PENDING,

    /** CDP file transmitted to bureau; awaiting production confirmation. */
    SENT,

    /** Bureau confirmed that all cards in the batch have been personalised and are ready for dispatch. */
    CONFIRMED,

    /** Transmission failed or bureau reported a production error. Check {@code notes} column for detail. */
    FAILED
}

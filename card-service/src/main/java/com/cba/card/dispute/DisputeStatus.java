package com.cba.card.dispute;

/**
 * Full scheme-compliant chargeback state machine.
 *
 * <pre>
 *   RAISED → RETRIEVAL_REQUESTED → CHARGEBACK_INITIATED
 *          → REPRESENTMENT → PRE_ARBITRATION → RESOLVED
 *   Any non-terminal state → WITHDRAWN
 * </pre>
 */
public enum DisputeStatus {

    /** Cardholder has filed the dispute; awaiting issuer action. */
    RAISED,

    /**
     * Issuer has formally requested transaction documentation from the acquirer.
     * A {@code RetrievalRequest} record exists. Scheme deadline applies.
     */
    RETRIEVAL_REQUESTED,

    /**
     * Formal chargeback filed with the card scheme.
     * A scheme reason code is attached; acquirer has a deadline to respond or representment.
     */
    CHARGEBACK_INITIATED,

    /**
     * Acquirer has countered the chargeback with a representment (evidence submission).
     * A {@code Representment} record exists. Issuer must escalate or accept by deadline.
     */
    REPRESENTMENT,

    /**
     * Escalated to the card scheme for arbitration.
     * Scheme will issue a binding ruling. Most expensive outcome — avoid if possible.
     */
    PRE_ARBITRATION,

    /**
     * Final resolution reached (issuer favour or acquirer favour).
     * See {@link CardDispute#getResolutionFavor()} for direction.
     */
    RESOLVED,

    /** Cardholder or issuer has withdrawn the dispute. Terminal state. */
    WITHDRAWN
}

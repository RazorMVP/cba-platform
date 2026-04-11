package com.cba.card.bureau;

/**
 * Per-card personalization status within a bureau job.
 */
public enum BureauJobItemStatus {

    /** Card included in batch; personalization not yet confirmed by bureau. */
    PENDING,

    /** Bureau confirmed chip personalization complete; card is PRODUCED. */
    PERSONALIZED,

    /** Bureau reported a personalization error for this specific card. */
    FAILED
}

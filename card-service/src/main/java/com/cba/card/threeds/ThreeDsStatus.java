package com.cba.card.threeds;

public enum ThreeDsStatus {
    /** AReq received; risk evaluation in progress. */
    INITIATED,
    /** Challenge OTP sent to cardholder; waiting for verification. */
    CHALLENGE_REQUIRED,
    /** Cardholder successfully authenticated; CAVV generated. */
    AUTHENTICATED,
    /** Authentication failed (wrong OTP / max attempts exceeded). */
    FAILED,
    /** Authentication rejected by ACS (invalid card, blocked, etc.). */
    REJECTED
}

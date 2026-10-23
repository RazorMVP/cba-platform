package com.cba.account;

public enum AccountHoldStatus {
    ACTIVE,
    RELEASED,
    EXPIRED,
    /** A card hold that was cleared: the account was debited and the journal posted. */
    CAPTURED,
    /** A completed card debit that was reversed: the account was credited back. */
    REFUNDED
}

package com.cba.payment;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED,
    /** An external payment the beneficiary's bank sent back after the gateway accepted it; the source was credited back. */
    RETURNED
}

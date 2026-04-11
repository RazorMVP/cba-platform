package com.cba.card.bin;

/**
 * Card scheme types — mirrors the fep-service SchemeType enum.
 * Kept in sync manually; do not rename values without updating fep-service.
 */
public enum SchemeType {
    VISA,
    MASTERCARD,
    VERVE,
    AFRIGO,
    UNION_PAY,
    UNKNOWN
}

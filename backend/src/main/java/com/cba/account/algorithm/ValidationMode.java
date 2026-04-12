package com.cba.account.algorithm;

/**
 * Controls how strictly inbound account numbers are validated.
 *
 * STRICT   — validates that the number is structurally correct for any bank
 *            (check digit passes). Used for inter-bank payments where the
 *            destination account belongs to a different institution.
 *
 * PARANOID — additionally enforces that the number's bank code matches this
 *            tenant's own configured bank code. Appropriate for intra-bank
 *            operations and new account creation.
 */
public enum ValidationMode {
    STRICT,
    PARANOID
}

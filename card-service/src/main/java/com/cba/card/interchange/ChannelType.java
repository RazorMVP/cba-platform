package com.cba.card.interchange;

/**
 * Transaction channel for interchange qualification.
 *
 * <p>CARD_PRESENT covers all physical card interactions (chip, contactless, mag stripe).
 * CNP covers card-not-present scenarios (e-commerce, MOTO, recurring).
 *
 * <p>Mag stripe (SWIPE) maps to CARD_PRESENT but typically qualifies for a higher-cost
 * "standard" rate tier because the card wasn't authenticated via chip. This is handled
 * by seeding separate rate rows in {@code interchange_rates} keyed to the appropriate
 * card product rather than via a separate channel type.
 */
public enum ChannelType {
    CARD_PRESENT,
    CNP
}

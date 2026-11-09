package com.cba.cardsettlement.dto;

import java.math.BigDecimal;

/**
 * The outcome of posting a card scheme settlement. A refusal is a result, not an error.
 *
 * @param approved             true when the settlement is posted (now, or by an earlier identical request)
 * @param repeat               true when an earlier identical request already posted it (no second journal)
 * @param journalTransactionId the journal that posted it
 * @param reason               why not: REFERENCE_CONFLICT (same reference, other figures), UNKNOWN_SCHEME,
 *                             DATE_NOT_CLOSED (settlement date not before today), ZERO_AMOUNT (reported and expected
 *                             both zero), INVALID_AMOUNT (more decimals than the currency has), UNKNOWN_CURRENCY,
 *                             DIFFERENCE_OVER_TOLERANCE (|reported − expected| above the currency's tolerance)
 * @param expectedAmount       the expected net (major units, signed)
 * @param amount               the reported net (major units, signed)
 * @param difference           reported − expected; posted to GL 1004 when recorded
 */
public record CardSchemeSettlementResult(boolean approved, boolean repeat, String reference,
                                         String journalTransactionId, String reason,
                                         BigDecimal expectedAmount, BigDecimal amount, BigDecimal difference) {}

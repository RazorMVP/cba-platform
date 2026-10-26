package com.cba.cardsettlement.dto;

/**
 * The outcome of posting a card scheme settlement. A refusal is a result, not an error.
 *
 * @param approved             true when the settlement is posted (now, or by an earlier identical request)
 * @param repeat               true when an earlier identical request already posted it (no second journal)
 * @param journalTransactionId the journal that posted it
 * @param reason               why not: REFERENCE_CONFLICT (same reference, other figures), UNKNOWN_SCHEME,
 *                             DATE_NOT_CLOSED (settlement date not before today), ZERO_AMOUNT,
 *                             INVALID_AMOUNT (more decimals than the currency has), UNKNOWN_CURRENCY
 */
public record CardSchemeSettlementResult(boolean approved, boolean repeat, String reference,
                                         String journalTransactionId, String reason) {}

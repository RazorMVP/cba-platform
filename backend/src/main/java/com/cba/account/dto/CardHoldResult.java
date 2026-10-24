package com.cba.account.dto;

import com.cba.account.AccountHold;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The outcome of a card hold request, clearing (capture), single-message debit or release/refund.
 * A decline is a result, not an error.
 *
 * @param approved         true when the requested state holds (placed, captured, debited, released or
 *                         refunded — now or by an earlier identical request)
 * @param status           the hold's status (ACTIVE, RELEASED, EXPIRED, CAPTURED, REFUNDED), or null when there is no hold
 * @param reason           why not: INSUFFICIENT_FUNDS, ACCOUNT_NOT_ACTIVE, ACCOUNT_IN_LOCKIN_PERIOD,
 *                         CURRENCY_MISMATCH, HOLD_RELEASED, REFERENCE_CONFLICT, AMOUNT_EXCEEDS_HOLD,
 *                         HOLD_NOT_FOUND, HOLD_NOT_ACTIVE, ACCOUNT_CLOSED, INVALID_CARD_FEES; null when approved
 * @param availableBalance what remains spendable after this request (major units)
 * @param interchangeAmount    interchange the debit posted (major units; null when none)
 * @param interchangeDirection EARNED or PAID
 * @param schemeFeeAmount      scheme fees the debit posted
 * @param interchangeReversed  interchange the refund reversed
 * @param schemeFeeReversed    scheme fees the refund reversed
 */
public record CardHoldResult(boolean approved, UUID holdId, String reference, String status,
                             String reason, BigDecimal availableBalance,
                             BigDecimal interchangeAmount, String interchangeDirection, BigDecimal schemeFeeAmount,
                             BigDecimal interchangeReversed, BigDecimal schemeFeeReversed) {

    public CardHoldResult(boolean approved, UUID holdId, String reference, String status,
                          String reason, BigDecimal availableBalance) {
        this(approved, holdId, reference, status, reason, availableBalance, null, null, null, null, null);
    }

    /** The same result carrying the figures stored on the hold (what was actually posted). */
    public CardHoldResult withCharges(AccountHold hold) {
        return new CardHoldResult(approved, holdId, reference, status, reason, availableBalance,
                hold.getInterchangeAmount(),
                hold.getInterchangeDirection() == null ? null : hold.getInterchangeDirection().name(),
                hold.getSchemeFeeAmount(), hold.getInterchangeReversed(), hold.getSchemeFeeReversed());
    }
}

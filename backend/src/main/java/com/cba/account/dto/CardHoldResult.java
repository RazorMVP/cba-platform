package com.cba.account.dto;

import com.cba.account.AccountHold;
import com.cba.account.CardHoldRefund;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The outcome of a card hold request, clearing (capture), single-message debit or release/refund.
 * A decline is a result, not an error.
 *
 * @param approved         true when the requested state holds (placed, captured, debited, released or
 *                         refunded — now or by an earlier identical request)
 * @param status           the hold's status (ACTIVE, RELEASED, EXPIRED, CAPTURED, REFUNDED), or null when there is no hold.
 *                         A partly refunded debit stays CAPTURED; it is REFUNDED once refunds reach the captured amount
 * @param reason           why not: INSUFFICIENT_FUNDS, ACCOUNT_NOT_ACTIVE, ACCOUNT_IN_LOCKIN_PERIOD,
 *                         CURRENCY_MISMATCH, HOLD_RELEASED, REFERENCE_CONFLICT, AMOUNT_EXCEEDS_HOLD,
 *                         AMOUNT_EXCEEDS_CAPTURED, ALREADY_REFUNDED, HOLD_NOT_FOUND, HOLD_NOT_ACTIVE,
 *                         ACCOUNT_CLOSED, INVALID_CARD_FEES; null when approved
 * @param availableBalance what remains spendable after this request (major units)
 * @param interchangeAmount    interchange the debit posted (major units; null when none)
 * @param interchangeDirection EARNED or PAID
 * @param schemeFeeAmount      scheme fees the debit posted
 * @param interchangeReversed  interchange reversed by all the debit's refunds so far (running total)
 * @param schemeFeeReversed    scheme fees reversed by all the debit's refunds so far (running total)
 * @param refundedAmount       amount refunded so far (running total; null when never refunded)
 * @param refund               the refund this request made — or, for a repeat, the one it repeats; null when
 *                             the request was not a refund (or was refused)
 */
public record CardHoldResult(boolean approved, UUID holdId, String reference, String status,
                             String reason, BigDecimal availableBalance,
                             BigDecimal interchangeAmount, String interchangeDirection, BigDecimal schemeFeeAmount,
                             BigDecimal interchangeReversed, BigDecimal schemeFeeReversed,
                             BigDecimal refundedAmount, Refund refund) {

    /**
     * One refund of a captured debit.
     *
     * @param reference           the reversal reference it is keyed by
     * @param amount              the amount it credited back (major units)
     * @param interchangeReversed interchange this refund reversed (its share)
     * @param schemeFeeReversed   scheme fees this refund reversed (its share)
     * @param repeat              true when this request repeated an earlier refund (nothing posted now)
     */
    public record Refund(String reference, BigDecimal amount, BigDecimal interchangeReversed,
                         BigDecimal schemeFeeReversed, boolean repeat) {

        public static Refund of(CardHoldRefund r, boolean repeat) {
            return new Refund(r.getRefundReference(), r.getAmount(),
                    r.getInterchangeReversed() == null ? BigDecimal.ZERO : r.getInterchangeReversed(),
                    r.getSchemeFeeReversed() == null ? BigDecimal.ZERO : r.getSchemeFeeReversed(), repeat);
        }
    }

    public CardHoldResult(boolean approved, UUID holdId, String reference, String status,
                          String reason, BigDecimal availableBalance) {
        this(approved, holdId, reference, status, reason, availableBalance, null, null, null, null, null, null, null);
    }

    /** The same result carrying the figures stored on the hold (what was actually posted, and refunded so far). */
    public CardHoldResult withCharges(AccountHold hold) {
        return new CardHoldResult(approved, holdId, reference, status, reason, availableBalance,
                hold.getInterchangeAmount(),
                hold.getInterchangeDirection() == null ? null : hold.getInterchangeDirection().name(),
                hold.getSchemeFeeAmount(), hold.getInterchangeReversed(), hold.getSchemeFeeReversed(),
                hold.getRefundedAmount(), refund);
    }

    /** The same result naming the refund it made (or repeats). */
    public CardHoldResult withRefund(Refund made) {
        return new CardHoldResult(approved, holdId, reference, status, reason, availableBalance,
                interchangeAmount, interchangeDirection, schemeFeeAmount, interchangeReversed, schemeFeeReversed,
                refundedAmount, made);
    }
}

package com.cba.card.interchange;

import java.math.BigDecimal;

/**
 * Interchange and scheme fees on one transaction from the issuer's side (backlog 15b), major units of
 * the transaction currency, rounded to the currency's digits.
 *
 * @param direction      EARNED for a purchase, PAID for cash; null when nothing applies
 * @param schemeFee      every scheme fee on the transaction — what the backend posts DR 5006 / CR 2300.
 *                       Includes {@code crossBorderFee} and {@code currencyFee}
 * @param rateApplied    the rate row used, for audit and disputes
 * @param crossBorderFee the CROSS_BORDER part of {@code schemeFee} (acquirer abroad; backlog 15d)
 * @param currencyFee    the INTERNATIONAL_SERVICE part of {@code schemeFee} (transaction currency ≠ card currency)
 * @param countryUnknown the acquirer's country couldn't be compared with the card's, so no cross-border
 *                       fee was charged
 */
public record IssuerCharges(String scheme, BigDecimal interchange, InterchangeDirection direction,
                            BigDecimal schemeFee, String rateApplied,
                            BigDecimal crossBorderFee, BigDecimal currencyFee, boolean countryUnknown) {

    public IssuerCharges {
        interchange = interchange == null ? BigDecimal.ZERO : interchange;
        schemeFee = schemeFee == null ? BigDecimal.ZERO : schemeFee;
        crossBorderFee = crossBorderFee == null ? BigDecimal.ZERO : crossBorderFee;
        currencyFee = currencyFee == null ? BigDecimal.ZERO : currencyFee;
    }

    /** Without a cross-border / currency split (a domestic transaction, or figures from the backend). */
    public IssuerCharges(String scheme, BigDecimal interchange, InterchangeDirection direction,
                         BigDecimal schemeFee, String rateApplied) {
        this(scheme, interchange, direction, schemeFee, rateApplied, null, null, false);
    }

    /** No interchange and no fees (refunds, a zero amount, or an engine failure). */
    public static IssuerCharges none(String scheme) {
        return new IssuerCharges(scheme, BigDecimal.ZERO, null, BigDecimal.ZERO, "NONE");
    }

    /**
     * These figures (the backend's, which it posted) with the split and flag of {@code quoted} — the quote
     * they were posted from. The split is kept only when the totals agree; otherwise it would no longer
     * add up to {@link #schemeFee}.
     */
    public IssuerCharges withSplitFrom(IssuerCharges quoted) {
        if (quoted == null) return this;
        boolean sameTotal = schemeFee.compareTo(quoted.schemeFee()) == 0;
        return new IssuerCharges(scheme, interchange, direction, schemeFee, rateApplied,
                sameTotal ? quoted.crossBorderFee() : null, sameTotal ? quoted.currencyFee() : null,
                quoted.countryUnknown());
    }

    /**
     * What the bank owes the scheme for this transaction, per {@code gross}: interchange earned lowers
     * it, interchange paid and fees raise it.
     */
    public BigDecimal owedToScheme(BigDecimal gross) {
        BigDecimal ic = direction == InterchangeDirection.EARNED ? interchange.negate() : interchange;
        return gross.add(ic).add(schemeFee);
    }
}

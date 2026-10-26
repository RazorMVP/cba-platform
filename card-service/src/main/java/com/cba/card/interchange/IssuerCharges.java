package com.cba.card.interchange;

import java.math.BigDecimal;

/**
 * Interchange and scheme fees on one transaction from the issuer's side (backlog 15b), major units of
 * the transaction currency, rounded once to the currency's digits.
 *
 * @param direction   EARNED for a purchase, PAID for cash; null when nothing applies
 * @param rateApplied the rate row used, for audit and disputes
 */
public record IssuerCharges(String scheme, BigDecimal interchange, InterchangeDirection direction,
                            BigDecimal schemeFee, String rateApplied) {

    public IssuerCharges {
        interchange = interchange == null ? BigDecimal.ZERO : interchange;
        schemeFee = schemeFee == null ? BigDecimal.ZERO : schemeFee;
    }

    /** No interchange and no fees (prepaid, refunds, a zero amount, or an engine failure). */
    public static IssuerCharges none(String scheme) {
        return new IssuerCharges(scheme, BigDecimal.ZERO, null, BigDecimal.ZERO, "NONE");
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

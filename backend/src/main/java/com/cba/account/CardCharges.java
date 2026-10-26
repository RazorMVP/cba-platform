package com.cba.account;

import java.math.BigDecimal;

/**
 * What a card debit posts besides the amount: interchange (earned or paid) and card scheme fees,
 * major units of the account currency. Nulls read as zero, so a debit without them posts as in 15a.
 */
public record CardCharges(BigDecimal interchange, CardInterchangeDirection direction, BigDecimal schemeFee) {

    public static final CardCharges NONE = new CardCharges(BigDecimal.ZERO, null, BigDecimal.ZERO);

    public CardCharges {
        interchange = interchange == null ? BigDecimal.ZERO : interchange;
        schemeFee = schemeFee == null ? BigDecimal.ZERO : schemeFee;
    }

    /** The figures stored on a hold when it was debited. */
    public static CardCharges of(AccountHold hold) {
        return new CardCharges(hold.getInterchangeAmount(), hold.getInterchangeDirection(), hold.getSchemeFeeAmount());
    }

    public boolean isZero() {
        return interchange.signum() == 0 && schemeFee.signum() == 0;
    }
}

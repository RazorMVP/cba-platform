package com.cba.card.interchange;

import java.math.BigDecimal;

/**
 * Result of an interchange calculation from the issuer's side (backlog 15b), major units of the
 * card's currency.
 *
 * @param interchangeAmount   interchange on the transaction
 * @param schemeFeeAmount     every scheme fee: assessment, network, cross-border and currency conversion
 * @param netSettlementAmount what the issuer owes the scheme: gross − interchange earned + interchange paid + fees
 * @param rateApplied         the rate row used
 * @param direction           EARNED (purchase) or PAID (cash); null when nothing applies
 * @param crossBorderFee      the cross-border part of {@code schemeFeeAmount} (acquirer abroad; backlog 15d)
 * @param currencyFee         the currency-conversion (international service) part of {@code schemeFeeAmount}
 * @param countryUnknown      the acquirer's country couldn't be compared with the card's — no cross-border fee
 */
public record InterchangeResult(
        BigDecimal interchangeAmount,
        BigDecimal schemeFeeAmount,
        BigDecimal netSettlementAmount,
        String rateApplied,
        InterchangeDirection direction,
        BigDecimal crossBorderFee,
        BigDecimal currencyFee,
        boolean countryUnknown
) {}

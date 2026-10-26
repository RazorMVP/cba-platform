package com.cba.card.interchange;

import java.math.BigDecimal;

/**
 * Result of an interchange calculation from the issuer's side (backlog 15b), major units of the
 * transaction currency.
 *
 * @param interchangeAmount   interchange on the transaction
 * @param schemeFeeAmount     scheme assessment and network fees
 * @param netSettlementAmount what the issuer owes the scheme: gross − interchange earned + interchange paid + fees
 * @param rateApplied         the rate row used
 * @param direction           EARNED (purchase) or PAID (cash); null when nothing applies
 */
public record InterchangeResult(
        BigDecimal interchangeAmount,
        BigDecimal schemeFeeAmount,
        BigDecimal netSettlementAmount,
        String rateApplied,
        InterchangeDirection direction
) {}

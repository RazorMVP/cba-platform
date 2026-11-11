package com.cba.cardprepaid.dto;

import com.cba.account.CardCharges;
import com.cba.account.CardInterchangeDirection;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Prepaid spend cleared at batch close (backlog 15c): DR 2500 / CR 2300, with the interchange and scheme fees
 * card-service quoted (major units; optional, as on a debit-card capture).
 */
public record PrepaidClearingRequest(
        @NotBlank String reference,
        UUID cardId,
        BigDecimal amount,
        String currencyCode,
        String scheme,
        BigDecimal interchangeAmount,
        CardInterchangeDirection interchangeDirection,
        BigDecimal schemeFeeAmount) {

    public CardCharges charges() {
        return new CardCharges(interchangeAmount, interchangeDirection, schemeFeeAmount);
    }
}

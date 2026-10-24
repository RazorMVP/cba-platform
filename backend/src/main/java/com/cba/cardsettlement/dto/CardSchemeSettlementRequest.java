package com.cba.cardsettlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A card scheme's net settlement, as card-service recorded it from the scheme's settlement report.
 *
 * @param reference      card-service's key — the same reference never posts twice
 * @param scheme         VISA, MASTERCARD, VERVE, AFRIGO, UNIONPAY
 * @param settlementDate the business date the scheme settled
 * @param currencyCode   ISO 4217 alphabetic
 * @param amount         signed, major units: positive = the bank paid the scheme; negative = the scheme paid the bank
 */
public record CardSchemeSettlementRequest(
        @NotBlank @Size(max = 64) String reference,
        @NotBlank @Size(max = 20) String scheme,
        @NotNull LocalDate settlementDate,
        @NotBlank @Size(max = 3) String currencyCode,
        @NotNull BigDecimal amount
) {}

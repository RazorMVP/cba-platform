package com.cba.card.settlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A card scheme's settlement report, as operations read it from the scheme (backlog 15b).
 *
 * @param scheme         VISA, MASTERCARD, VERVE, AFRIGO, UNIONPAY
 * @param settlementDate the business date settled (before today)
 * @param currency       ISO 4217, alphabetic ("USD") or numeric ("840")
 * @param netAmount      major units, signed: positive = the bank pays the scheme; negative = the scheme pays the bank
 */
public record SchemeReportRequest(
        @NotBlank @Size(max = 20) String scheme,
        @NotNull LocalDate settlementDate,
        @NotBlank @Size(max = 3) String currency,
        @NotNull BigDecimal netAmount
) {}

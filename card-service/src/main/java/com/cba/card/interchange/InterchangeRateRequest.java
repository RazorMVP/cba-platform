package com.cba.card.interchange;

import com.cba.card.card.CardType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InterchangeRateRequest(
        @NotBlank String scheme,

        @NotNull CardType cardType,

        /** Optional — null means applies to all MCCs. */
        String mccCategory,

        @NotNull TransactionType transactionType,

        @NotNull ChannelType channel,

        @NotNull @DecimalMin("0.0") BigDecimal ratePercent,

        @NotNull @DecimalMin("0.0") BigDecimal fixedFee,

        @NotBlank @Size(min = 3, max = 3) String currencyCode,

        @NotNull LocalDate effectiveFrom,

        /** Null = indefinitely active. */
        LocalDate effectiveTo,

        boolean active
) {}

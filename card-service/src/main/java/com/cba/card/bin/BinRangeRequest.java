package com.cba.card.bin;

import com.cba.card.card.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a BIN range.
 *
 * <p>Keeps JPA entity state (generated ID, createdAt, version) out of
 * the public API surface. Validated before the service layer is invoked.
 */
public record BinRangeRequest(
        @NotBlank(message = "binStart is required")
        @Size(min = 6, max = 8, message = "BIN must be 6 or 8 digits")
        @Pattern(regexp = "\\d+", message = "BIN must contain only digits")
        String binStart,

        @NotBlank(message = "binEnd is required")
        @Size(min = 6, max = 8, message = "BIN must be 6 or 8 digits")
        @Pattern(regexp = "\\d+", message = "BIN must contain only digits")
        String binEnd,

        @NotNull(message = "scheme is required")
        SchemeType scheme,

        String productType,

        CardType cardType,

        /** ISO 3166 alpha-3 country code (e.g. USA, KEN, GHA). */
        @Size(max = 3)
        String countryCode,

        /** ISO 4217 numeric currency code (e.g. 840, 404, 288). */
        @Size(max = 3)
        String currencyCode,

        boolean active
) {}

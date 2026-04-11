package com.cba.card.terminal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for terminal simulator endpoints.
 *
 * <p>Covers all transaction types (purchase, withdrawal, balance, reversal, network).
 * Optional fields are null-safe — the service sets sensible defaults.
 */
public record SimulateRequest(
        /** Full PAN (16–19 digits). Required for all except network management. */
        String cardNumber,

        /** Expiry in YYMM format (e.g. "2712"). */
        String expiryDate,

        /** Transaction amount in major units (e.g. 25.00 for $25). */
        BigDecimal amount,

        /** ISO 4217 currency code (e.g. "840" for USD, "404" for KES). */
        String currency,

        /** ATM/POS terminal ID (8 chars). Defaults to "TERM0001". */
        String terminalId,

        /** Acquirer merchant ID (15 chars). Defaults to "MERCHANT000001 ". */
        String merchantId,

        /** Merchant name/location (up to 40 chars). */
        String merchantName,

        /** Merchant Category Code (4 digits). Defaults to "5411" (grocery). */
        String mcc,

        /**
         * Card entry mode.
         * One of: SWIPE (mag stripe), CHIP (EMV contact), CONTACTLESS (NFC).
         * Maps to DE22: 021=swipe, 051=chip, 071=contactless.
         */
        String entryMode,

        /**
         * ISO-0 format PIN block (hex-encoded, 16 hex chars = 8 bytes).
         * Optional — omit for contactless or CNP transactions.
         */
        String pinBlock,

        /**
         * Original STAN for reversals (DE11 of the original transaction).
         * Required for reversal transactions only.
         */
        String originalStan,

        /**
         * Original RRN for reversals (DE37 of the original transaction).
         * Required for reversal transactions only.
         */
        String originalRrn,

        /**
         * Network management code for 0800 messages.
         * "0001"=sign-on, "0002"=sign-off, "0301"=echo.
         */
        String networkCode
) {}

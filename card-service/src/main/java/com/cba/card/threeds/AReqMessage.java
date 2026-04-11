package com.cba.card.threeds;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound 3DS 2.x Authentication Request (AReq) from the Directory Server.
 *
 * <p>Only the fields consumed by this ACS are mapped here. The full EMVCo
 * 3DS 2.3 specification defines ~80+ fields; unused fields are ignored by
 * Jackson's default deserialization.
 *
 * <p>Field naming follows the EMVCo spec (camelCase, not snake_case) so that
 * the Directory Server's JSON is accepted without a custom deserializer.
 */
public record AReqMessage(

        /** Unique transaction ID assigned by the 3DS Server. Required. */
        @NotNull UUID threeDSServerTransID,

        /**
         * Transaction ID assigned by the Directory Server.
         * Absent for decoupled auth flows; nullable here.
         */
        UUID dsTransID,

        /** PAN of the card being authenticated. Required. */
        @NotBlank String acctNumber,

        /** Card expiry date in YYMM format. */
        String cardExpiryDate,

        /**
         * Message category: "01" = PA (Payment Authentication),
         * "02" = NPA (Non-Payment Authentication).
         */
        String messageCategory,

        /** Transaction type: "01" = goods/service purchase, "03" = cash advance, etc. */
        String transType,

        /** Purchase amount in minor units (cents). Null for NPA flows. */
        BigDecimal purchaseAmount,

        /** ISO 4217 numeric currency code for the purchase. */
        String purchaseCurrency,

        /** Purchase exponent (decimal places in currency). Default 2 for most currencies. */
        String purchaseExponent,

        /** Merchant name — displayed in the challenge page. */
        String merchantName,

        /** Merchant country code (ISO 3166-1 numeric). */
        String merchantCountryCode,

        /** Acquirer BIN. */
        String acquirerBIN,

        /** Acquirer merchant ID. */
        String acquirerMerchantID,

        /**
         * Device channel:
         * "01" = App-based, "02" = Browser, "03" = 3RI (3DS Requestor Initiated).
         */
        String deviceChannel,

        /**
         * Notification URL where the ACS posts the CReq/ARes result back to the
         * 3DS Server after challenge completion. Present for browser channel.
         */
        String notificationURL
) {
    /** Returns the purchase amount scaled to the given exponent, or zero for NPA. */
    public BigDecimal scaledAmount() {
        if (purchaseAmount == null) return BigDecimal.ZERO;
        int exp = purchaseExponent != null ? Integer.parseInt(purchaseExponent) : 2;
        return purchaseAmount.movePointLeft(exp);
    }
}

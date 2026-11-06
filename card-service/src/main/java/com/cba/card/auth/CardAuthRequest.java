package com.cba.card.auth;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Authorization request DTO received from fep-service.
 * Mirrors the fep-service AuthorizationRequest record — JSON-serializable.
 */
public record CardAuthRequest(
        String             pan,
        String             processingCode,
        BigDecimal         amount,          // in minor units (cents) as BigDecimal
        String             currencyCode,
        String             stan,
        String             terminalId,
        String             merchantId,
        String             merchantName,
        String             mcc,
        String             posEntryMode,
        String             posConditionCode,
        String             rrn,
        String             scheme,
        boolean            pinVerified,
        boolean            arqcValid,
        boolean            isFinancial,
        Map<String,String> schemeData,       // scheme-specific data (parsed private DEs / EMV tags); matches fep-service AuthorizationRequest.schemeData
        String             acquiringCountry, // DE19, ISO 3166 numeric (566 = Nigeria); null when the acquirer sent none
        BigDecimal         billingAmount,    // DE6 cardholder billing amount, minor units of billingCurrencyCode; null = same as DE4
        String             billingCurrencyCode, // DE51, ISO 4217 numeric; sent together with DE6
        String             billingConversionRate // DE10 as received; audit only
) {
    /** Without cardholder billing fields (DE6/DE51/DE10): the transaction is in the card's currency. */
    public CardAuthRequest(String pan, String processingCode, BigDecimal amount, String currencyCode, String stan,
                           String terminalId, String merchantId, String merchantName, String mcc, String posEntryMode,
                           String posConditionCode, String rrn, String scheme, boolean pinVerified, boolean arqcValid,
                           boolean isFinancial, Map<String, String> schemeData, String acquiringCountry) {
        this(pan, processingCode, amount, currencyCode, stan, terminalId, merchantId, merchantName, mcc, posEntryMode,
                posConditionCode, rrn, scheme, pinVerified, arqcValid, isFinancial, schemeData, acquiringCountry,
                null, null, null);
    }

    /** DE6 and/or DE51 present. */
    public boolean billingSent() {
        return billingAmount != null || (billingCurrencyCode != null && !billingCurrencyCode.isBlank());
    }
}

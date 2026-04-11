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
        Map<String,String> emvTags          // hex-encoded EMV TLV values; null for non-EMV
) {}

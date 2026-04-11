package com.cba.card.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Normalized, scheme-agnostic representation of one cleared transaction
 * ready for export.
 *
 * <p>Each {@link SettlementFileExporter} receives a list of these records
 * and is responsible for serializing them into its scheme-specific file
 * format. All monetary amounts are in minor units (cents / kobo / pesewas).
 *
 * <p>Fields marked "populated from authorization_log JOIN" are joined at
 * query time by {@link SettlementFileExportService} — the exporter receives
 * a fully-populated record and does not need to make additional DB calls.
 */
public record SettlementExportRecord(

        // ── Identity ─────────────────────────────────────────────────────────
        UUID   settlementItemId,
        UUID   authorizationLogId,

        // ── Card / Account ────────────────────────────────────────────────────
        /** Masked PAN for logging (e.g. "411111******1111"). Never the full PAN. */
        String maskedPan,
        /** Full PAN — provided to the exporter for file serialization only. */
        String pan,
        String cardType,          // DEBIT / CREDIT / PREPAID
        String scheme,            // VISA / MASTERCARD / VERVE / AFRIGO / UNIONPAY

        // ── Transaction ───────────────────────────────────────────────────────
        String mti,               // 0100 / 0200
        String stan,              // DE11
        String rrn,               // DE37
        String authCode,          // DE38
        String processingCode,    // DE3
        String entryMode,         // DE22

        // ── Amounts (all in minor units, ISO 4217 numeric currency code) ──────
        BigDecimal grossAmount,
        BigDecimal interchangeAmount,
        BigDecimal schemeFeeAmount,
        BigDecimal netAmount,
        String     currencyCode,  // ISO 4217 numeric (e.g. "840")

        // ── Merchant ──────────────────────────────────────────────────────────
        String merchantId,        // DE42
        String merchantName,      // DE43
        String mcc,               // DE18
        String terminalId,        // DE41
        String acquirerBin,       // acquirer's BIN — required by BASE II / IPM headers

        // ── Dates ─────────────────────────────────────────────────────────────
        LocalDate transactionDate,
        LocalDate settlementDate

) {
    /** PAN masked for safe logging: first 6 + ****** + last 4. */
    public static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "****";
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }
}

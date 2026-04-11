package com.cba.openbanking.card;

import java.time.Instant;
import java.util.Map;

/**
 * Maps card-service DTOs to UK Open Banking v3.1 response shapes.
 *
 * <p>This is the anti-corruption layer between the card domain and the OB API contract.
 * All shape translation lives here — {@link com.cba.openbanking.AccountInfoController}
 * calls these methods directly without knowing card-service internals.
 */
public final class CardAccountAdapter {

    private CardAccountAdapter() {}

    /**
     * Maps a card to an OB Accounts resource.
     *
     * <pre>
     * accountType    → "Account" with accountSubType indicating card category
     * AccountId      → card UUID (used as the OB AccountId for balance/tx lookups)
     * Identification → "****{last4}" — never expose full PAN
     * </pre>
     */
    public static Map<String, Object> toObAccount(CardServiceClient.CardDto card) {
        String subType = switch (card.cardType() != null ? card.cardType() : "DEBIT") {
            case "CREDIT"  -> "CreditCard";
            case "PREPAID" -> "PrepaidCard";
            default        -> "DebitCard";
        };
        String maskedPan = "****" + (card.panSuffix() != null ? card.panSuffix() : "****");
        String expiry = formatExpiry(card.expiryDate());   // YYMM → MM/YY

        return Map.of(
            "AccountId",       card.id() != null ? card.id().toString() : "",
            "AccountType",     "Card",
            "AccountSubType",  subType,
            "Status",          card.status() != null ? card.status() : "Unknown",
            "AccountNumber",   maskedPan,
            "ExpiryDate",      expiry,
            "Product",         card.productName() != null ? card.productName() : ""
        );
    }

    /**
     * Maps a card balance to an OB Balances resource.
     *
     * <p>Credit cards use {@code CreditLine} type; debit/prepaid use {@code ClosingAvailable}.
     */
    public static Map<String, Object> toObBalance(
            String cardId,
            CardServiceClient.CardBalanceDto balance,
            String currencyCode) {

        String balType = "CREDIT".equals(balance.cardType()) ? "CreditLine" : "ClosingAvailable";
        String amount  = balance.availableBalance() != null
                ? balance.availableBalance().toPlainString() : "0.00";

        return Map.of(
            "AccountId",            cardId,
            "Amount",               Map.of("Amount", amount, "Currency", currencyCode != null ? currencyCode : "USD"),
            "CreditDebitIndicator", "Credit",
            "Type",                 balType,
            "DateTime",             Instant.now().toString()
        );
    }

    /**
     * Maps a card authorization log entry to an OB Transaction resource.
     *
     * <p>Only approved transactions ({@code responseCode = "00"}) are surfaced as booked;
     * declined authorizations are surfaced as {@code Pending} with status {@code Declined}.
     */
    public static Map<String, Object> toObTransaction(
            String cardId,
            CardServiceClient.CardAuthDto auth) {

        boolean approved = "00".equals(auth.responseCode());
        String status    = approved ? "Booked" : "Pending";
        String cdi       = auth.financial() ? "Debit" : "Credit";
        String amount    = auth.amount() != null ? auth.amount().toPlainString() : "0.00";
        String ccy       = auth.currencyCode() != null ? auth.currencyCode() : "840";
        String merchant  = auth.merchantName() != null ? auth.merchantName() : "";
        String txTime    = auth.createdAt() != null ? auth.createdAt().toString() : Instant.now().toString();

        return Map.of(
            "TransactionId",            auth.id() != null ? auth.id().toString() : "",
            "AccountId",                cardId,
            "CreditDebitIndicator",     cdi,
            "Status",                   status,
            "BookingDateTime",          txTime,
            "Amount",                   Map.of("Amount", amount, "Currency", ccy),
            "TransactionInformation",   merchant,
            "MerchantDetails",          Map.of("MerchantName", merchant, "MerchantCategoryCode", auth.mcc() != null ? auth.mcc() : ""),
            "SupplementaryData",        Map.of("STAN", auth.stan() != null ? auth.stan() : "",
                                               "RRN",  auth.rrn()  != null ? auth.rrn()  : "",
                                               "ResponseCode", auth.responseCode() != null ? auth.responseCode() : "")
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Converts card expiry from {@code YYMM} (ISO 8583 DE14) to {@code MM/YY} display format. */
    private static String formatExpiry(String yymm) {
        if (yymm == null || yymm.length() != 4) return "";
        return yymm.substring(2) + "/" + yymm.substring(0, 2);   // YYMM → MM/YY
    }
}

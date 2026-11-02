package com.cba.card.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Amount formatting shared by the scheme exporters.
 *
 * <p>Settlement amounts are ISO 4217 minor units (DE4), but they come out of {@code NUMERIC(19,4)} columns
 * with four decimals: 25.00 USD is {@code 2500.0000}. Writing {@code toPlainString()} without the point gave
 * {@code 25000000} — ten thousand times the amount (backlog 21). These helpers drop the scale instead, and
 * refuse what can't be written exactly: a fractional minor amount is bad data, and a fixed-width field that
 * would cut digits off would send a different amount.
 */
final class SettlementAmounts {

    private SettlementAmounts() {}

    /** The amount as a whole number of minor units; null is 0. Throws if it has a fractional part. */
    static long minor(BigDecimal amount) {
        if (amount == null) return 0L;
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Settlement amount is not a whole number of minor units: "
                    + amount.toPlainString(), e);
        }
    }

    /** {@link #minor} zero-padded to {@code width} digits. Throws if the amount is negative or too wide. */
    static String minorDigits(BigDecimal amount, int width) {
        return minorDigits(minor(amount), width);
    }

    /** A minor-unit amount (e.g. a file total) zero-padded to {@code width} digits; same checks. */
    static String minorDigits(long minor, int width) {
        if (minor < 0) {
            throw new IllegalArgumentException("Negative settlement amount: " + minor);
        }
        String digits = Long.toString(minor);
        if (digits.length() > width) {
            throw new IllegalArgumentException("Settlement amount " + digits + " doesn't fit " + width + " digits");
        }
        return "0".repeat(width - digits.length()) + digits;
    }

    /** Sum of the records' gross amounts in minor units. */
    static long totalMinor(List<SettlementExportRecord> records) {
        return records.stream().mapToLong(r -> minor(r.grossAmount())).sum();
    }

    /** The record's currency (ISO 4217 numeric, as DE49); a record without one can't be exported. */
    static String currency(SettlementExportRecord r) {
        if (r.currencyCode() == null || r.currencyCode().isBlank()) {
            throw new IllegalArgumentException("Settlement record " + r.settlementItemId() + " has no currency");
        }
        return r.currencyCode();
    }
}

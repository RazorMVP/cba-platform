package com.cba.card.interchange;

import java.util.Locale;

/**
 * Card scheme names as card-service stores them. {@code UNIONPAY} is canonical — the FEP, the
 * settlement exporters and chargebacks use it; V3 seeded {@code UNION_PAY}, so a UnionPay
 * authorization never matched a rate until V13.
 */
public final class Schemes {

    private Schemes() {}

    /** Upper case, {@code UNION_PAY} → {@code UNIONPAY}, null or blank → {@code UNKNOWN}. */
    public static String normalize(String scheme) {
        if (scheme == null || scheme.isBlank()) return "UNKNOWN";
        String s = scheme.trim().toUpperCase(Locale.ROOT);
        return "UNION_PAY".equals(s) ? "UNIONPAY" : s;
    }
}

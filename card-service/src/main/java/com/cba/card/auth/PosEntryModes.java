package com.cba.card.auth;

import java.util.Locale;
import java.util.Set;

/**
 * POS entry mode (ISO 8583 DE22) classification, shared by the cardholder controls and interchange.
 *
 * <p>DE22's first two digits are the PAN entry mode: 02 magstripe, 05 chip, 07 contactless chip, 80 chip
 * fallback to magstripe, 90 full magstripe, 91 contactless magstripe — all card present. 01 (manual key
 * entry), 81 (e-commerce), 10 (credential on file) and anything else, blank included, are card not
 * present. The terminal simulator's names (CHIP, CONTACTLESS, SWIPE) are accepted too.
 */
public final class PosEntryModes {

    private static final Set<String> CARD_PRESENT = Set.of("02", "05", "07", "80", "90", "91");
    private static final Set<String> CONTACTLESS  = Set.of("07", "91");

    private PosEntryModes() {}

    public static boolean isCardPresent(String entryMode) {
        String m = normalize(entryMode);
        if (m == null) return false;
        return switch (m) {
            case "CHIP", "CONTACTLESS", "SWIPE" -> true;
            default -> m.length() >= 2 && CARD_PRESENT.contains(m.substring(0, 2));
        };
    }

    public static boolean isContactless(String entryMode) {
        String m = normalize(entryMode);
        if (m == null) return false;
        return "CONTACTLESS".equals(m) || (m.length() >= 2 && CONTACTLESS.contains(m.substring(0, 2)));
    }

    private static String normalize(String entryMode) {
        return entryMode == null || entryMode.isBlank() ? null : entryMode.trim().toUpperCase(Locale.ROOT);
    }
}

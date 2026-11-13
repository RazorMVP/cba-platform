package com.cba.card.auth;

/**
 * Where a card transaction happened relative to the card's home country (backlog 5, 15d).
 *
 * <p>The acquirer's country is DE19 (ISO 3166 numeric); the card's home country is its BIN range's
 * {@code country_code} (alpha-3). One rule for every use: the international control (where
 * {@link #UNKNOWN} fails closed) and the cross-border scheme fee (where {@link #UNKNOWN} charges nothing
 * and flags the settlement item).
 */
public enum CardCountry {
    /** The acquirer is in the card's home country. */
    DOMESTIC,
    /** The acquirer is in another country. */
    CROSS_BORDER,
    /** No DE19, a code that isn't ISO 3166, or a BIN range without a country. */
    UNKNOWN;

    /**
     * @param homeAlpha3      the card's home country, ISO 3166 alpha-3 (null when its BIN range has none)
     * @param acquirerNumeric DE19 as sent, ISO 3166 numeric (null when not sent)
     */
    public static CardCountry of(String homeAlpha3, String acquirerNumeric) {
        String acquirer = CountryCodes.alpha3(acquirerNumeric);
        if (acquirer == null || homeAlpha3 == null || homeAlpha3.isBlank()) return UNKNOWN;
        return acquirer.equalsIgnoreCase(homeAlpha3.trim()) ? DOMESTIC : CROSS_BORDER;
    }
}

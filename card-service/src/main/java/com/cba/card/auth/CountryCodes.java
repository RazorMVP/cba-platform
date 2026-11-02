package com.cba.card.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ISO 3166-1 numeric → alpha-3 country codes.
 *
 * <p>ISO 8583 carries countries as 3-digit numeric codes (DE19 acquiring institution country, 566 =
 * Nigeria), while {@code bin_ranges.country_code} holds alpha-3 (NGA). The JDK only knows alpha-2 and
 * alpha-3, so the mapping is the classpath table {@code iso3166-countries.csv} (all 249 countries).
 */
public final class CountryCodes {

    private static final Map<String, String> NUMERIC_TO_ALPHA3 = load();

    private CountryCodes() {}

    /** Alpha-3 code for a numeric code ({@code "566"} → {@code "NGA"}); {@code null} if blank or unknown. */
    public static String alpha3(String numeric) {
        if (numeric == null || numeric.isBlank()) return null;
        String n = numeric.trim();
        if (!n.matches("\\d{1,3}")) return null;
        return NUMERIC_TO_ALPHA3.get("0".repeat(3 - n.length()) + n);
    }

    static Map<String, String> all() {
        return Map.copyOf(NUMERIC_TO_ALPHA3);
    }

    private static Map<String, String> load() {
        try (InputStream in = CountryCodes.class.getResourceAsStream("/iso3166-countries.csv")) {
            if (in == null) throw new IllegalStateException("iso3166-countries.csv missing from the classpath");
            Map<String, String> map = new HashMap<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("numeric")) continue;
                String[] parts = line.split(",");
                map.put(parts[0].trim(), parts[1].trim().toUpperCase(Locale.ROOT));
            }
            return map;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

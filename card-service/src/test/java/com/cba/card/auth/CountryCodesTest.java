package com.cba.card.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CountryCodesTest {

    @Test
    @DisplayName("known numeric codes map to their alpha-3 code, with or without leading zeros")
    void knownCodes() {
        assertThat(CountryCodes.alpha3("566")).isEqualTo("NGA");
        assertThat(CountryCodes.alpha3("840")).isEqualTo("USA");
        assertThat(CountryCodes.alpha3("404")).isEqualTo("KEN");
        assertThat(CountryCodes.alpha3("288")).isEqualTo("GHA");
        assertThat(CountryCodes.alpha3("036")).isEqualTo("AUS");
        assertThat(CountryCodes.alpha3("36")).isEqualTo("AUS");
    }

    @Test
    @DisplayName("blank, unassigned or non-numeric input gives null")
    void unknownCodes() {
        assertThat(CountryCodes.alpha3(null)).isNull();
        assertThat(CountryCodes.alpha3("")).isNull();
        assertThat(CountryCodes.alpha3("999")).isNull();
        assertThat(CountryCodes.alpha3("NGA")).isNull();
        assertThat(CountryCodes.alpha3("5661")).isNull();
    }

    @Test
    @DisplayName("the table holds all 249 ISO 3166-1 countries, every alpha-3 one the JDK knows, no duplicates")
    void tableMatchesTheJdk() {
        Set<String> jdk = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA3);
        var table = CountryCodes.all();
        assertThat(table).hasSize(249);
        assertThat(table.keySet()).allMatch(n -> n.matches("\\d{3}"));
        assertThat(jdk).containsAll(table.values());
        assertThat(Set.copyOf(table.values())).hasSize(table.size());
    }
}

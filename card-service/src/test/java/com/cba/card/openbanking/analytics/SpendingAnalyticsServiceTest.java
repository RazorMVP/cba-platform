package com.cba.card.openbanking.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpendingAnalyticsService#categoryFor} — the MCC → category
 * lookup that drives spend-by-category analytics. The SQL aggregations are
 * integration territory (need a real DB); this lookup is the pure domain logic.
 */
class SpendingAnalyticsServiceTest {

    @Test
    @DisplayName("known MCCs map to their merchant category")
    void knownMccs() {
        assertThat(SpendingAnalyticsService.categoryFor("5812")).isEqualTo("Dining");
        assertThat(SpendingAnalyticsService.categoryFor("4111")).isEqualTo("Travel");
        assertThat(SpendingAnalyticsService.categoryFor("5541")).isEqualTo("Fuel");
        assertThat(SpendingAnalyticsService.categoryFor("5411")).isEqualTo("Grocery");
        assertThat(SpendingAnalyticsService.categoryFor("8011")).isEqualTo("Healthcare");
        assertThat(SpendingAnalyticsService.categoryFor("6011")).isEqualTo("ATM/Cash");
    }

    @Test
    @DisplayName("an unknown or null MCC falls back to Other")
    void unknownMccIsOther() {
        assertThat(SpendingAnalyticsService.categoryFor("9999")).isEqualTo("Other");
        assertThat(SpendingAnalyticsService.categoryFor(null)).isEqualTo("Other");
    }
}

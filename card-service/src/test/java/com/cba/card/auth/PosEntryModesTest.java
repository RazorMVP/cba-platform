package com.cba.card.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PosEntryModesTest {

    @ParameterizedTest
    @ValueSource(strings = {"021", "051", "071", "801", "901", "911", "CHIP", "contactless", "SWIPE"})
    @DisplayName("magstripe, chip, contactless and fallback entry modes are card present")
    void cardPresent(String mode) {
        assertThat(PosEntryModes.isCardPresent(mode)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"010", "012", "812", "100", "CNP", " "})
    @DisplayName("manual entry, e-commerce, credential on file and a missing DE22 are card not present")
    void cardNotPresent(String mode) {
        assertThat(PosEntryModes.isCardPresent(mode)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"071", "072", "911", "CONTACTLESS"})
    @DisplayName("contactless chip and contactless magstripe are contactless")
    void contactless(String mode) {
        assertThat(PosEntryModes.isContactless(mode)).isTrue();
    }

    @Test
    @DisplayName("chip, magstripe, e-commerce and blank are not contactless")
    void notContactless() {
        assertThat(PosEntryModes.isContactless("051")).isFalse();
        assertThat(PosEntryModes.isContactless("021")).isFalse();
        assertThat(PosEntryModes.isContactless("812")).isFalse();
        assertThat(PosEntryModes.isContactless(null)).isFalse();
    }
}

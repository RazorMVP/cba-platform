package com.cba.fep.emv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for the {@link EmvData} value object (tag accessors + immutability). */
class EmvDataTest {

    private EmvData sample() {
        Map<String, byte[]> tags = new HashMap<>();
        tags.put("9F26", new byte[]{0x11, 0x22, (byte) 0xAB});
        return new EmvData(tags);
    }

    @Test
    @DisplayName("getTag returns bytes for present tag, null for absent")
    void getTag() {
        EmvData d = sample();
        assertThat(d.getTag("9F26")).containsExactly(0x11, 0x22, 0xAB);
        assertThat(d.getTag("9F99")).isNull();
    }

    @Test
    @DisplayName("getTagHex renders uppercase hex; null for absent")
    void getTagHex() {
        EmvData d = sample();
        assertThat(d.getTagHex("9F26")).isEqualTo("1122AB");
        assertThat(d.getTagHex("9F99")).isNull();
    }

    @Test
    @DisplayName("accessors are case-insensitive on the tag key")
    void caseInsensitive() {
        EmvData d = sample();
        assertThat(d.hasTag("9f26")).isTrue();
        assertThat(d.getTagHex("9f26")).isEqualTo("1122AB");
    }

    @Test
    @DisplayName("the tag map is defensively copied and unmodifiable")
    void immutability() {
        Map<String, byte[]> source = new HashMap<>();
        source.put("82", new byte[]{0x58, 0x00});
        EmvData d = new EmvData(source);

        // Mutating the source after construction must not affect the EmvData
        source.put("95", new byte[]{0x00});
        assertThat(d.hasTag("95")).isFalse();

        // The internal map is unmodifiable
        assertThatThrownBy(() -> d.tags().put("9C", new byte[]{0x00}))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

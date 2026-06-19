package com.cba.card.threeds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CavvGenerator} — the 3DS Cardholder Authentication Verification
 * Value. The CAVV must be deterministic for a given (card, txn) and bind to those
 * inputs, so the issuer can verify it later.
 */
class CavvGeneratorTest {

    private final CavvGenerator generator = new CavvGenerator();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(generator, "masterKey", "unit-test-master-key");
    }

    @Test
    @DisplayName("same inputs produce the same CAVV (deterministic)")
    void deterministic() {
        UUID card = UUID.randomUUID();
        UUID acs = UUID.randomUUID();
        String a = generator.generate(card, acs, new BigDecimal("100.00"), "840", "05");
        String b = generator.generate(card, acs, new BigDecimal("100.00"), "840", "05");
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("CAVV is a 28-character Base64 string (20 bytes)")
    void cavvLength() {
        String cavv = generator.generate(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), "840", "05");
        assertThat(cavv).hasSize(28);
    }

    @Test
    @DisplayName("a different card yields a different CAVV (bound to card)")
    void boundToCard() {
        UUID acs = UUID.randomUUID();
        String a = generator.generate(UUID.randomUUID(), acs, new BigDecimal("10.00"), "840", "05");
        String b = generator.generate(UUID.randomUUID(), acs, new BigDecimal("10.00"), "840", "05");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("a different amount yields a different CAVV (bound to amount)")
    void boundToAmount() {
        UUID card = UUID.randomUUID();
        UUID acs = UUID.randomUUID();
        String a = generator.generate(card, acs, new BigDecimal("10.00"), "840", "05");
        String b = generator.generate(card, acs, new BigDecimal("20.00"), "840", "05");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("a missing currency is rejected (no silent USD default)")
    void currencyRequired() {
        assertThatThrownBy(() -> generator.generate(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), null, "05"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("hmacHex is deterministic and 64 hex chars (SHA-256)")
    void hmacHex() {
        byte[] key = "k".getBytes();
        String h1 = generator.hmacHex(key, "value");
        String h2 = generator.hmacHex(key, "value");
        assertThat(h1).isEqualTo(h2).hasSize(64).matches("[0-9a-f]{64}");
    }
}

package com.cba.partner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PartnerApiKeys — SHA-256 hashing")
class PartnerApiKeysTest {

    @Test
    @DisplayName("hash is deterministic, 64 hex chars, and distinct per input")
    void hash_isDeterministicAndHex64() {
        String key = "cba_abc123";
        String h1 = PartnerApiKeys.hash(key);
        String h2 = PartnerApiKeys.hash(key);

        assertThat(h1).isEqualTo(h2).hasSize(64).matches("[0-9a-f]+");
        assertThat(PartnerApiKeys.hash("cba_different")).isNotEqualTo(h1);
    }
}

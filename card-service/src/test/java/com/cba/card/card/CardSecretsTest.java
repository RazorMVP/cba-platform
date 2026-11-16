package com.cba.card.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CardSecrets — PAN/DPAN encryption at rest and the key check (backlog 2 PR B)")
class CardSecretsTest {

    private final CardSecrets secrets = CardSecretsTestSupport.cardSecrets();

    @Test
    @DisplayName("round trip: ENC1 ciphertext without the digits, decrypts to the original")
    void roundTrip() {
        String stored = secrets.encrypt("4111111111111111");
        assertThat(stored).startsWith("ENC1:").doesNotContain("4111111111111111").doesNotContain("41111111");
        assertThat(secrets.decrypt(stored)).isEqualTo("4111111111111111");
        assertThat(CardSecrets.isEncrypted(stored)).isTrue();
    }

    @Test
    @DisplayName("random IV: the same PAN encrypts differently each time")
    void randomIv() {
        assertThat(secrets.encrypt("4111111111111111")).isNotEqualTo(secrets.encrypt("4111111111111111"));
    }

    @Test
    @DisplayName("a value that isn't ENC1 ciphertext is refused, and the message doesn't echo it")
    void refusesClearValue() {
        assertThatThrownBy(() -> secrets.decrypt("4111111111111111"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("4111");
        assertThatThrownBy(() -> secrets.decrypt("ENC(4111111111111111)")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("null stays null")
    void nulls() {
        assertThat(secrets.encrypt(null)).isNull();
        assertThat(secrets.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("key check: blank, short and public keys refused; a long secret one accepted")
    void keyCheck() {
        assertThatThrownBy(() -> CardSecrets.checkKey(null, false)).hasMessageContaining("not set");
        assertThatThrownBy(() -> CardSecrets.checkKey("  ", false)).hasMessageContaining("not set");
        for (String publicKey : CardSecrets.KNOWN_PUBLIC_KEYS) {
            assertThatThrownBy(() -> CardSecrets.checkKey(publicKey, false))
                    .as(publicKey).hasMessageContaining("public default");
        }
        assertThatThrownBy(() -> CardSecrets.checkKey("x".repeat(31), false)).hasMessageContaining("shorter");
        assertThatCode(() -> CardSecrets.checkKey("k".repeat(32), false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allow-dev-key accepts the public dev keys (tests, docker-compose, local runs)")
    void devKeyAllowed() {
        assertThatCode(() -> CardSecrets.checkKey("dev-encryption-key-change-in-prod", true)).doesNotThrowAnyException();
        assertThatCode(() -> CardSecrets.checkKey(null, true)).doesNotThrowAnyException();
    }
}

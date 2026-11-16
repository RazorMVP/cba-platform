package com.cba.card.card;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Encrypts the card secrets card-service stores — the PAN ({@code cards.pan_encrypted}) and the DPAN
 * ({@code token_vault.dpan_encrypted}) — and decrypts them only where the clear value is needed
 * (de-tokenization; the acquirer settlement file). PCI DSS 4.0 §3.5.1: a stored PAN must be unreadable.
 *
 * <p>Deliberately not a JPA {@code AttributeConverter}: a converter would decrypt the PAN on every card load,
 * the authorization path included, and hand it to any code holding a {@code Card}. Here the entity holds the
 * ciphertext and the few callers that need the clear value ask for it.
 *
 * <p>Stored form: {@code ENC1:} + Jasypt ciphertext ({@code PBEWITHHMACSHA512ANDAES_256}, random IV, from
 * {@code jasypt.encryptor.*}; jasypt-spring-boot's single {@code StringEncryptor} bean, injected by type).
 * {@link #decrypt} refuses anything without the prefix, so a value that isn't
 * proven ciphertext is never treated as one; the version leaves room for key rotation ({@code ENC2:}).
 *
 * <p>Until backlog 2 PR B these columns held clear values (no converter existed). {@code V24__EncryptCardSecrets}
 * encrypts the existing rows before the app serves anything.
 */
@Component
public class CardSecrets {

    public static final String PREFIX = "ENC1:";

    /** Public or placeholder keys that must never protect real card data (PCI DSS 4.0 §3.6.1, §3.7). */
    static final Set<String> KNOWN_PUBLIC_KEYS = Set.of(
            "dev-encryption-key-change-in-prod",
            "dev_encryption_key_at_least_32_chars",
            "<CHANGE_ME_MIN_32_CHARS>",
            "test-encryption-key-do-not-use-in-production");

    static final int MIN_KEY_LENGTH = 32;

    private final StringEncryptor encryptor;

    public CardSecrets(StringEncryptor encryptor,
                       @Value("${jasypt.encryptor.password:}") String key,
                       @Value("${card.crypto.allow-dev-key:false}") boolean allowDevKey) {
        checkKey(key, allowDevKey);
        this.encryptor = encryptor;
    }

    /**
     * Refuses a blank, short or publicly known key unless dev mode allows it ({@code card.crypto.allow-dev-key},
     * env {@code CARD_ALLOW_DEV_ENCRYPTION_KEY} — set in tests, docker-compose and local runs, never in
     * Kubernetes). Runs when this bean is created, before {@code V24__EncryptCardSecrets} (which depends on it)
     * writes anything: data encrypted under a key that's then replaced would be unreadable.
     */
    static void checkKey(String key, boolean allowDevKey) {
        if (allowDevKey) {
            return;
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("card-service: jasypt.encryptor.password (ENCRYPTION_KEY / "
                    + "JASYPT_ENCRYPTOR_PASSWORD) is not set — card numbers can't be encrypted.");
        }
        if (KNOWN_PUBLIC_KEYS.contains(key)) {
            throw new IllegalStateException("card-service: the encryption key is a public default or placeholder. "
                    + "Set a secret key of at least " + MIN_KEY_LENGTH + " characters "
                    + "(or CARD_ALLOW_DEV_ENCRYPTION_KEY=true for local development only).");
        }
        if (key.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException("card-service: the encryption key is shorter than "
                    + MIN_KEY_LENGTH + " characters.");
        }
    }

    /** {@code null} stays {@code null}. */
    public String encrypt(String clear) {
        return clear == null ? null : PREFIX + encryptor.encrypt(clear);
    }

    /** {@code null} stays {@code null}; anything without {@link #PREFIX} is refused. */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!isEncrypted(stored)) {
            throw new IllegalStateException("Stored card secret is not ENC1 ciphertext — refusing to use it");
        }
        return encryptor.decrypt(stored.substring(PREFIX.length()));
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}

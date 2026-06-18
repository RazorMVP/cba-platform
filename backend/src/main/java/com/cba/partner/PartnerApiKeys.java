package com.cba.partner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashing for partner API keys.
 *
 * <p>API keys are high-entropy random tokens (32 bytes), so a fast deterministic
 * SHA-256 hash is the correct choice — it allows O(1) lookup by hash. (bcrypt is
 * salted and non-deterministic, so it cannot be used to look a key up by its hash.)
 */
public final class PartnerApiKeys {

    private PartnerApiKeys() {}

    public static String hash(String rawKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

package com.cba.common.crypto;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * AES-256 field-level encryptor for PII columns.
 * Uses PBEWITHHMACSHA512ANDAES_256 (FIPS-approved) — NOT the weaker PBEWithMD5AndDES.
 * Secret key sourced from ENCRYPTION_KEY environment variable.
 */
@Component
public class FieldEncryptor {

    /**
     * Seed-data sentinel. Jasypt uses a random salt + IV, so a valid ciphertext cannot
     * be hand-written into a Flyway migration (no static string round-trips). Demo
     * migrations (V2, V4) therefore store PII as {@code DEMO_ENC:<plaintext>} and rely on
     * {@link #decrypt(String)} to recognise the marker and return the plaintext. Real
     * writes always go through jasypt encryption, so the marker only ever appears in seed
     * rows and self-heals to real ciphertext on the first update.
     */
    static final String DEMO_PREFIX = "DEMO_ENC:";

    @Value("${cba.encryption.secret-key}")
    private String secretKey;

    private PooledPBEStringEncryptor encryptor;

    @PostConstruct
    public void init() {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(secretKey);
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("4");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");

        encryptor = new PooledPBEStringEncryptor();
        encryptor.setConfig(config);
    }

    public String encrypt(String value) {
        return value == null ? null : encryptor.encrypt(value);
    }

    public String decrypt(String value) {
        if (value == null) {
            return null;
        }
        // Seed rows carry plaintext behind the DEMO_ENC: marker (see DEMO_PREFIX) —
        // pass the plaintext through rather than feeding it to jasypt (which would throw).
        if (value.startsWith(DEMO_PREFIX)) {
            return value.substring(DEMO_PREFIX.length());
        }
        return encryptor.decrypt(value);
    }
}

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
        return value == null ? null : encryptor.decrypt(value);
    }
}

package com.cba.card.card;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;

/** A real {@link CardSecrets} for unit tests: the production algorithm and random IV, a test key. */
public final class CardSecretsTestSupport {

    public static final String TEST_KEY = "unit-test-card-secrets-key-0123456789";

    private CardSecretsTestSupport() {
    }

    public static CardSecrets cardSecrets() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setPassword(TEST_KEY);
        return new CardSecrets(encryptor, TEST_KEY, false);
    }
}

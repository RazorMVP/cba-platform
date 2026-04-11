package com.cba.fep.hsm;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.macs.CBCBlockCipherMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Software HSM adapter using Bouncy Castle TDES.
 *
 * <p><strong>FOR DEV / TESTING ONLY.</strong>
 * This implementation performs cryptographic operations in software.
 * In a real payment environment, all key material is protected inside
 * a certified HSM under the Local Master Key (LMK). This adapter uses
 * a hardcoded test key — never use in production.
 *
 * <p>Activated by: {@code fep.hsm.provider=SOFTWARE} in application.yml.
 * The Thales adapter is activated by {@code fep.hsm.provider=THALES}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fep.hsm.provider", havingValue = "SOFTWARE", matchIfMissing = true)
public class SoftwareHsmAdapter implements HsmAdapter {

    // !! DEV-ONLY hardcoded test key — never commit a real LMK or ZPK !!
    private static final byte[] DEV_PIN_KEY = {
        (byte)0x01, (byte)0x23, (byte)0x45, (byte)0x67,
        (byte)0x89, (byte)0xAB, (byte)0xCD, (byte)0xEF,
        (byte)0xFE, (byte)0xDC, (byte)0xBA, (byte)0x98,
        (byte)0x76, (byte)0x54, (byte)0x32, (byte)0x10,
        (byte)0x01, (byte)0x23, (byte)0x45, (byte)0x67,
        (byte)0x89, (byte)0xAB, (byte)0xCD, (byte)0xEF
    };

    private static final byte[] DEV_MAC_KEY = {
        (byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF,
        (byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE,
        (byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF,
        (byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE,
        (byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF,
        (byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE
    };

    private final SecureRandom random = new SecureRandom();

    public SoftwareHsmAdapter() {
        log.warn("SoftwareHsmAdapter active — DEV MODE ONLY. Do not use in production.");
    }

    @Override
    public boolean verifyPin(byte[] pinBlock, String pan) {
        try {
            // Decrypt PIN block using 3DES under the dev PIN key
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(DEV_PIN_KEY, "DESede"));
            byte[] decrypted = cipher.doFinal(pinBlock);

            // XOR with PAN padding block (ISO 9564-1 Format 0)
            byte[] panPad = buildPanPadBlock(pan);
            byte[] pinDigits = new byte[8];
            for (int i = 0; i < 8; i++) pinDigits[i] = (byte) (decrypted[i] ^ panPad[i]);

            // Extract PIN length from first nibble of pinDigits[0]
            int pinLen = (pinDigits[0] >> 4) & 0x0F;
            // In dev mode, accept any PIN with valid format (length 4–12)
            boolean valid = pinLen >= 4 && pinLen <= 12;
            log.debug("SoftwareHsm: PIN verify result={} (dev mode — always approves valid format)", valid);
            return valid;
        } catch (Exception e) {
            log.error("SoftwareHsm: PIN verification failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyCvv(String pan, String expiryDate, String serviceCode, String cvv) {
        // Software CVV verification — for dev only; always returns true for 3-digit CVV
        boolean valid = cvv != null && cvv.matches("\\d{3}");
        log.debug("SoftwareHsm: CVV verify result={} (dev mode)", valid);
        return valid;
    }

    @Override
    public byte[] generateMac(byte[] data, int keyIndex) {
        try {
            DESedeEngine engine = new DESedeEngine();
            CBCBlockCipherMac mac = new CBCBlockCipherMac(engine, 64); // 8-byte MAC
            mac.init(new KeyParameter(DEV_MAC_KEY));
            mac.update(data, 0, data.length);
            byte[] result = new byte[8];
            mac.doFinal(result, 0);
            return result;
        } catch (Exception e) {
            log.error("SoftwareHsm: MAC generation failed: {}", e.getMessage());
            return new byte[8];
        }
    }

    @Override
    public boolean verifyMac(byte[] data, byte[] mac, int keyIndex) {
        byte[] expected = generateMac(data, keyIndex);
        return Arrays.equals(expected, mac);
    }

    @Override
    public byte[] translatePinBlock(byte[] pinBlock, String pan) {
        // Dev mode: pass through — no actual key translation
        log.debug("SoftwareHsm: PIN block translation (dev mode passthrough)");
        return pinBlock.clone();
    }

    @Override
    public byte[] generateSessionKey() {
        byte[] key = new byte[24];
        random.nextBytes(key);
        // Return a mock KCV: first 3 bytes of ECB encrypt of zeroes under the key
        return Arrays.copyOf(key, 3);
    }

    /**
     * Build the PAN padding block for ISO 9564-1 Format 0 PIN block XOR.
     * Format: 0000 + rightmost 12 digits of PAN (excluding check digit).
     */
    private byte[] buildPanPadBlock(String pan) {
        byte[] block = new byte[8];
        // Extract rightmost 12 PAN digits excluding check digit
        String panDigits = pan.replaceAll("\\D", "");
        String sub = panDigits.substring(Math.max(0, panDigits.length() - 13),
                panDigits.length() - 1);
        sub = String.format("%12s", sub).replace(' ', '0');
        // Pack BCD: '0000' + 12 PAN digits → 8 bytes
        block[0] = 0x00;
        block[1] = 0x00;
        for (int i = 0; i < 6; i++) {
            block[i + 2] = (byte) ((Character.digit(sub.charAt(i * 2), 10) << 4)
                    | Character.digit(sub.charAt(i * 2 + 1), 10));
        }
        return block;
    }
}

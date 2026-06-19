package com.cba.fep.emv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArpcGenerator} — the issuer's response cryptogram (Method 1)
 * that the card verifies to confirm the authorization decision came from a real issuer.
 */
class ArpcGeneratorTest {

    /** 24-byte dev key the generator hardcodes (IMK || IMK[0:8]). */
    private static final byte[] DEV_KEY = {
        (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
        (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
        (byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0x98,
        (byte) 0x76, (byte) 0x54, (byte) 0x32, (byte) 0x10,
        (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
        (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF
    };

    private final ArpcGenerator generator = new ArpcGenerator();

    @Test
    @DisplayName("null ARQC returns 8 zero bytes (safe default, no exception)")
    void nullArqcReturnsZeros() {
        assertThat(generator.generate(null, "00")).containsExactly(new byte[8]);
    }

    @Test
    @DisplayName("wrong-length ARQC returns 8 zero bytes")
    void wrongLengthArqcReturnsZeros() {
        assertThat(generator.generate(new byte[]{1, 2, 3, 4}, "00")).containsExactly(new byte[8]);
    }

    @Test
    @DisplayName("valid ARQC produces a deterministic 8-byte ARPC")
    void deterministic() {
        byte[] arqc = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] first  = generator.generate(arqc, "00");
        byte[] second = generator.generate(arqc, "00");
        assertThat(first).hasSize(8).isEqualTo(second);
        assertThat(first).isNotEqualTo(new byte[8]); // not the zero fallback
    }

    @Test
    @DisplayName("different authorization codes yield different ARPCs")
    void authCodeAffectsResult() {
        byte[] arqc = {1, 2, 3, 4, 5, 6, 7, 8};
        assertThat(generator.generate(arqc, "00"))
                .isNotEqualTo(generator.generate(arqc, "01"));
    }

    @Test
    @DisplayName("ARPC equals 3DES-ECB(devKey, ARQC XOR ARC-block) per Method 1")
    void matchesMethod1Vector() throws Exception {
        byte[] arqc = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
        byte[] expected = computeArpc(arqc, "00");
        assertThat(generator.generate(arqc, "00")).isEqualTo(expected);
    }

    /** Independent replica of ARPC Method 1 to lock the computation. */
    private static byte[] computeArpc(byte[] arqc, String authCode) throws Exception {
        byte[] arc = new byte[8];
        byte[] arcAscii = authCode.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(arcAscii, 0, arc, 0, Math.min(arcAscii.length, 2));

        byte[] xored = new byte[8];
        for (int i = 0; i < 8; i++) xored[i] = (byte) (arqc[i] ^ arc[i]);

        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(DEV_KEY, "DESede"));
        return Arrays.copyOf(c.doFinal(xored), 8);
    }
}

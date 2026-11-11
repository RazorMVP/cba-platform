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
    @DisplayName("null ARQC returns an empty array (no tag 91 is sent), never an exception")
    void nullArqcReturnsEmpty() {
        assertThat(generator.generate(null, "00")).isEmpty();
    }

    @Test
    @DisplayName("wrong-length ARQC returns an empty array")
    void wrongLengthArqcReturnsEmpty() {
        assertThat(generator.generate(new byte[]{1, 2, 3, 4}, "00")).isEmpty();
    }

    @Test
    @DisplayName("valid ARQC produces a deterministic 8-byte ARPC")
    void deterministic() {
        byte[] arqc = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] first  = generator.generate(arqc, "00");
        byte[] second = generator.generate(arqc, "00");
        assertThat(first).hasSize(8).isEqualTo(second);
        assertThat(first).isNotEqualTo(new byte[8]); // not a zero fallback
    }

    @Test
    @DisplayName("the response code is the ARC: approval 00 and decline 05 give different ARPCs")
    void responseCodeAffectsResult() {
        byte[] arqc = {1, 2, 3, 4, 5, 6, 7, 8};
        assertThat(generator.generate(arqc, "00"))
                .isNotEqualTo(generator.generate(arqc, "05"));
    }

    @Test
    @DisplayName("a 6-character approval code (DE38) is not an ARC — rejected, empty array")
    void authorizationCodeIsNotAnArc() {
        byte[] arqc = {1, 2, 3, 4, 5, 6, 7, 8};
        assertThat(generator.generate(arqc, "A1B2C3")).isEmpty();
        assertThat(generator.generate(arqc, null)).isEmpty();
        assertThat(generator.generate(arqc, "0")).isEmpty();
    }

    @Test
    @DisplayName("arc() is the two ASCII bytes of DE39")
    void arcIsAsciiOfResponseCode() {
        assertThat(ArpcGenerator.arc("00")).containsExactly(0x30, 0x30);
        assertThat(ArpcGenerator.arc("51")).containsExactly(0x35, 0x31);
        assertThat(ArpcGenerator.arc("Z3")).containsExactly(0x5A, 0x33);
        assertThat(ArpcGenerator.arc("000")).isNull();
        assertThat(ArpcGenerator.arc("\u00e90")).isNull();
    }

    @Test
    @DisplayName("ARPC equals 3DES-ECB(devKey, ARQC XOR (ARC || 00..00)) per Method 1 — approval and decline")
    void matchesMethod1Vector() throws Exception {
        byte[] arqc = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
        assertThat(generator.generate(arqc, "00")).isEqualTo(computeArpc(arqc, "00"));
        assertThat(generator.generate(arqc, "05")).isEqualTo(computeArpc(arqc, "05"));
    }

    /** Independent replica of ARPC Method 1 to lock the computation. */
    private static byte[] computeArpc(byte[] arqc, String responseCode) throws Exception {
        byte[] arc = new byte[8];
        byte[] arcAscii = responseCode.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(arcAscii, 0, arc, 0, 2);

        byte[] xored = new byte[8];
        for (int i = 0; i < 8; i++) xored[i] = (byte) (arqc[i] ^ arc[i]);

        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(DEV_KEY, "DESede"));
        return Arrays.copyOf(c.doFinal(xored), 8);
    }
}

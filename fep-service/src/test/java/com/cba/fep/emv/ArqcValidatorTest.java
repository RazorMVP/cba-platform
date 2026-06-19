package com.cba.fep.emv;

import com.cba.fep.hsm.HsmAdapter;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.macs.CBCBlockCipherMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArqcValidator} — the chip-card cryptogram check that decides
 * whether a transaction came from a genuine card. A false positive here approves
 * a cloned card; a false negative declines a legitimate one.
 *
 * <p>The validator injects an {@link HsmAdapter} but never dereferences it in the
 * dev path (it uses a hardcoded {@code DEV_IMK}), so a no-op stub is sufficient —
 * no Mockito (which cannot mock concrete classes on this Java 25 host).
 *
 * <p>The round-trip test re-implements the documented EMV TDES derivation with the
 * same dev key to produce a <em>genuine</em> ARQC. This is intentional duplication:
 * it locks the security behaviour so a one-sided change to the production algorithm
 * breaks the test rather than silently changing what counts as "valid".
 */
class ArqcValidatorTest {

    /** Same dev IMK the validator hardcodes (EMV issuer master key, dev only). */
    private static final byte[] DEV_IMK = {
        (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
        (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
        (byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0x98,
        (byte) 0x76, (byte) 0x54, (byte) 0x32, (byte) 0x10
    };

    private final ArqcValidator validator = new ArqcValidator(new NoOpHsm());

    // ── Offline CID decision logic (no cryptography involved) ──────────────────

    @Test
    @DisplayName("CID=TC (offline approved) is accepted without an ARQC")
    void offlineApprovedAccepted() {
        Map<String, byte[]> tags = new HashMap<>();
        tags.put(EmvTag.CRYPTOGRAM_INFORMATION_DATA, new byte[]{(byte) 0x40}); // TC
        assertThat(validator.validate(new EmvData(tags), "4111111111111111")).isTrue();
    }

    @Test
    @DisplayName("CID=AAC (offline declined) is rejected")
    void offlineDeclinedRejected() {
        Map<String, byte[]> tags = new HashMap<>();
        tags.put(EmvTag.CRYPTOGRAM_INFORMATION_DATA, new byte[]{(byte) 0x00}); // AAC
        assertThat(validator.validate(new EmvData(tags), "4111111111111111")).isFalse();
    }

    @Test
    @DisplayName("missing ARQC tag is rejected (cannot validate what isn't there)")
    void missingArqcRejected() {
        Map<String, byte[]> tags = new HashMap<>();
        tags.put(EmvTag.APPLICATION_TRANSACTION_COUNTER, new byte[]{0x00, 0x1C});
        // CID absent -> falls through to online path; ARQC missing -> false
        assertThat(validator.validate(new EmvData(tags), "4111111111111111")).isFalse();
    }

    @Test
    @DisplayName("missing ATC tag is rejected")
    void missingAtcRejected() {
        Map<String, byte[]> tags = new HashMap<>();
        tags.put(EmvTag.ARQC, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertThat(validator.validate(new EmvData(tags), "4111111111111111")).isFalse();
    }

    @Test
    @DisplayName("a forged/garbage ARQC is rejected (validator is not a no-op pass)")
    void garbageArqcRejected() {
        Map<String, byte[]> tags = onlineTags(new byte[]{0x00, 0x1C});
        tags.put(EmvTag.ARQC, new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF,
                                          (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
        assertThat(validator.validate(new EmvData(tags), "4111111111111111")).isFalse();
    }

    // ── Round-trip: a genuine ARQC passes, a tampered one fails ────────────────

    @Test
    @DisplayName("a genuine ARQC (computed with the dev key) validates as true")
    void genuineArqcAccepted() throws Exception {
        byte[] atc = {0x00, 0x1C};
        Map<String, byte[]> tags = onlineTags(atc);
        byte[] genuine = computeArqc(tags, atc);
        tags.put(EmvTag.ARQC, genuine);

        assertThat(validator.validate(new EmvData(tags), "4111111111111111")).isTrue();
    }

    @Test
    @DisplayName("a single-bit tamper of a genuine ARQC fails validation")
    void tamperedArqcRejected() throws Exception {
        byte[] atc = {0x00, 0x1C};
        Map<String, byte[]> tags = onlineTags(atc);
        byte[] genuine = computeArqc(tags, atc);
        genuine[0] ^= 0x01; // flip one bit
        tags.put(EmvTag.ARQC, genuine);

        assertThat(validator.validate(new EmvData(tags), "4111111111111111")).isFalse();
    }

    // ── Helpers: build the cryptogram input + replicate the production crypto ───

    /** The DE55 tags that feed the cryptogram, minus the ARQC itself. CID=ARQC (0x80). */
    private static Map<String, byte[]> onlineTags(byte[] atc) {
        Map<String, byte[]> tags = new HashMap<>();
        tags.put(EmvTag.AMOUNT_AUTHORISED,         new byte[]{0x00, 0x00, 0x00, 0x00, 0x10, 0x00}); // 9F02
        tags.put(EmvTag.AMOUNT_OTHER,              new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00}); // 9F03
        tags.put(EmvTag.TRANSACTION_CURRENCY_CODE, new byte[]{0x08, 0x40});                         // 5F2A
        tags.put(EmvTag.TRANSACTION_DATE,          new byte[]{0x26, 0x06, 0x19});                   // 9A
        tags.put(EmvTag.TRANSACTION_TYPE,          new byte[]{0x00});                               // 9C
        tags.put(EmvTag.APPLICATION_TRANSACTION_COUNTER, atc);                                      // 9F36
        tags.put(EmvTag.TERMINAL_VERIFICATION_RESULTS,   new byte[]{0,0,0,0,0});                    // 95
        tags.put(EmvTag.CRYPTOGRAM_INFORMATION_DATA,     new byte[]{(byte) 0x80});                  // 9F27 = ARQC
        return tags;
    }

    /** Mirror of ArqcValidator's TDES path: derive SK from ATC, CBC-MAC the CDOL block. */
    private static byte[] computeArqc(Map<String, byte[]> tags, byte[] atc) throws Exception {
        byte[] sk = deriveSessionKey(atc);
        byte[] data = buildCryptogramData(tags);
        return computeCbcMac(sk, data); // 8 bytes
    }

    private static byte[] deriveSessionKey(byte[] atc) throws Exception {
        byte[] left  = {atc[0], atc[1], (byte) 0xF0, 0x00, atc[0], atc[1], (byte) 0x0F, 0x00};
        byte[] right = {atc[0], atc[1], (byte) 0x0F, 0x00, atc[0], atc[1], (byte) 0xF0, 0x00};
        byte[] skLeft  = tripleDesEcb(left);
        byte[] skRight = tripleDesEcb(right);
        byte[] sk = new byte[16];
        System.arraycopy(skLeft,  0, sk, 0, 8);
        System.arraycopy(skRight, 0, sk, 8, 8);
        return sk;
    }

    private static byte[] buildCryptogramData(Map<String, byte[]> tags) {
        String[] order = {
            EmvTag.AMOUNT_AUTHORISED, EmvTag.AMOUNT_OTHER, EmvTag.TRANSACTION_CURRENCY_CODE,
            EmvTag.TRANSACTION_DATE, EmvTag.TRANSACTION_TYPE,
            EmvTag.APPLICATION_TRANSACTION_COUNTER, EmvTag.TERMINAL_VERIFICATION_RESULTS
        };
        int total = 0;
        for (String t : order) if (tags.get(t) != null) total += tags.get(t).length;
        int padded = ((total + 7) / 8) * 8;
        byte[] data = new byte[padded];
        int pos = 0;
        for (String t : order) {
            byte[] v = tags.get(t);
            if (v != null) { System.arraycopy(v, 0, data, pos, v.length); pos += v.length; }
        }
        if (pos < padded) data[pos] = (byte) 0x80;
        return data;
    }

    /** 3DES-ECB of one 8-byte block with k24 = IMK(16) || IMK[0:8]. */
    private static byte[] tripleDesEcb(byte[] block) throws Exception {
        byte[] k24 = new byte[24];
        System.arraycopy(DEV_IMK, 0, k24, 0, 16);
        System.arraycopy(DEV_IMK, 0, k24, 16, 8);
        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k24, "DESede"));
        return c.doFinal(block);
    }

    /** CBC-MAC (DESede, 64-bit MAC) with k24 = sk(16) || sk[0:8] — matches BouncyCastle path. */
    private static byte[] computeCbcMac(byte[] sk, byte[] data) {
        byte[] k24 = new byte[24];
        System.arraycopy(sk, 0, k24, 0, 16);
        System.arraycopy(sk, 0, k24, 16, 8);
        CBCBlockCipherMac mac = new CBCBlockCipherMac(new DESedeEngine(), 64);
        mac.init(new KeyParameter(k24));
        mac.update(data, 0, data.length);
        byte[] out = new byte[8];
        mac.doFinal(out, 0);
        return out;
    }

    /** No-op HsmAdapter — the validator's dev path never calls it. */
    private static final class NoOpHsm implements HsmAdapter {
        @Override public boolean verifyPin(byte[] pinBlock, String pan) { return true; }
        @Override public boolean verifyCvv(String pan, String expiryDate, String serviceCode, String cvv) { return true; }
        @Override public byte[] generateMac(byte[] data, int keyIndex) { return new byte[8]; }
        @Override public boolean verifyMac(byte[] data, byte[] mac, int keyIndex) { return true; }
        @Override public byte[] translatePinBlock(byte[] pinBlock, String pan) { return pinBlock; }
        @Override public byte[] generateSessionKey() { return new byte[16]; }
    }
}

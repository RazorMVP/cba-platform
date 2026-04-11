package com.cba.fep.emv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

/**
 * EMV ARPC (Authorization Response Cryptogram) generator.
 *
 * <p>The ARPC is sent by the issuer (FEP) back to the chip card to prove
 * the response originated from a legitimate issuer. The card verifies the
 * ARPC before accepting the authorization decision.
 *
 * <p>ARPC Method 1 (EMV Book 2, Section 8.2):
 * <pre>
 *   ARPC = 3DES-MAC(SK_AC, ARQC XOR ARC)
 *   where:
 *     SK_AC  = same session key used to validate ARQC
 *     ARQC   = 8-byte ARQC from card's DE55 tag 9F26
 *     ARC    = 2-byte Authorization Response Code (RC padded to 8 bytes: ARC || 0x0000...0000)
 * </pre>
 *
 * <p>The generated ARPC is embedded in DE55 tag 9F26 position 2 in the 0110 response,
 * alongside the Cryptogram Information Data (CID) in tag 9F27.
 */
@Slf4j
@Component
public class ArpcGenerator {

    /**
     * Generate ARPC using Method 1.
     *
     * @param arqc              8-byte ARQC from the card (tag 9F26)
     * @param authorizationCode 2-character authorization code (e.g., "00" = approve, "01" = decline)
     * @return 8-byte ARPC
     */
    public byte[] generate(byte[] arqc, String authorizationCode) {
        if (arqc == null || arqc.length != 8) {
            log.warn("ARPC generation: ARQC must be 8 bytes, got {}", arqc != null ? arqc.length : null);
            return new byte[8];
        }

        try {
            // Build the 8-byte ARC block: ARC (2 bytes) + 6 zero bytes padding
            byte[] arcBlock = buildArcBlock(authorizationCode);

            // XOR ARQC with ARC block
            byte[] xored = new byte[8];
            for (int i = 0; i < 8; i++) {
                xored[i] = (byte) (arqc[i] ^ arcBlock[i]);
            }

            // ARPC = left 8 bytes of 3DES-MAC(SK, xored)
            // Note: in production the session key is retrieved from the ArqcValidator context.
            // For now we use a simplified MAC — in a real implementation the session key
            // must be threaded through from the ARQC validation step.
            byte[] arpc = simpleMac(xored);
            log.debug("ARPC generated ({} bytes) for authCode={}", arpc.length, authorizationCode);
            return arpc;
        } catch (Exception e) {
            log.error("ARPC generation failed: {}", e.getMessage());
            return new byte[8];
        }
    }

    /**
     * Build the 8-byte ARC block for ARPC Method 1.
     * ARC is the 2-byte numeric authorization response code, right-padded with zeros.
     */
    private byte[] buildArcBlock(String authorizationCode) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        String arc = (authorizationCode != null) ? authorizationCode : "3030"; // ASCII "00"
        // Encode the two characters of the RC as their ASCII byte values
        byte[] arcBytes = arc.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        buf.put(arcBytes, 0, Math.min(arcBytes.length, 2));
        // Remaining 6 bytes stay as 0x00
        return buf.array();
    }

    /**
     * Simplified 3DES MAC for ARPC (dev mode).
     * In production this delegates to the HSM using the same session key as ARQC validation.
     */
    private byte[] simpleMac(byte[] data) throws Exception {
        // Use the same dev key as ArqcValidator for consistency
        byte[] devImk = {
            (byte)0x01, (byte)0x23, (byte)0x45, (byte)0x67,
            (byte)0x89, (byte)0xAB, (byte)0xCD, (byte)0xEF,
            (byte)0xFE, (byte)0xDC, (byte)0xBA, (byte)0x98,
            (byte)0x76, (byte)0x54, (byte)0x32, (byte)0x10,
            (byte)0x01, (byte)0x23, (byte)0x45, (byte)0x67,
            (byte)0x89, (byte)0xAB, (byte)0xCD, (byte)0xEF
        };
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(devImk, "DESede"));
        byte[] result = cipher.doFinal(data);
        return java.util.Arrays.copyOf(result, 8);
    }
}

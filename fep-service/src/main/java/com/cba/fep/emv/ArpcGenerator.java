package com.cba.fep.emv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * EMV ARPC (Authorization Response Cryptogram) generator.
 *
 * <p>The ARPC is sent by the issuer (FEP) back to the chip card to prove
 * the response originated from a legitimate issuer. The card verifies the
 * ARPC before accepting the authorization decision.
 *
 * <p>ARPC Method 1 (EMV 4.4 Book 2, §8.2.1):
 * <pre>
 *   ARPC = 3DES(SK_AC, ARQC XOR (ARC || 00 00 00 00 00 00))
 *   where:
 *     SK_AC  = same session key used to validate ARQC
 *     ARQC   = 8-byte ARQC from card's DE55 tag 9F26
 *     ARC    = 2-byte Authorisation Response Code = the two ASCII characters of DE39
 *              (e.g. "00" → 30 30, "05" → 30 35)
 * </pre>
 *
 * <p>The response DE55 then carries tag 91 Issuer Authentication Data = ARPC (8) || ARC (2) — see
 * {@link com.cba.fep.scheme.SchemeAdapter#embedArpc}. It is generated for declines as well as approvals:
 * the card authenticates the decision either way.
 *
 * <p><b>Dev only:</b> the 3DES uses a hardcoded dev key, not the session key derived for the ARQC; the
 * production version runs in the HSM (backlog item 4, HSM half).
 */
@Slf4j
@Component
public class ArpcGenerator {

    /**
     * The 2-byte ARC for a response code: its two ASCII characters.
     *
     * @param responseCode DE39 ("00", "05", "51" …)
     * @return the two ARC bytes, or {@code null} if the response code isn't exactly two printable ASCII characters
     */
    public static byte[] arc(String responseCode) {
        if (responseCode == null || responseCode.length() != 2) {
            return null;
        }
        for (int i = 0; i < 2; i++) {
            char c = responseCode.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return null;
            }
        }
        return responseCode.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Generate ARPC using Method 1.
     *
     * @param arqc         8-byte ARQC from the card (tag 9F26)
     * @param responseCode DE39 of the response being sent — its two characters are the ARC
     * @return 8-byte ARPC, or an empty array when the ARQC isn't 8 bytes, the response code isn't a
     *         valid ARC, or the computation fails (callers then send no tag 91)
     */
    public byte[] generate(byte[] arqc, String responseCode) {
        if (arqc == null || arqc.length != 8) {
            log.warn("ARPC generation: ARQC must be 8 bytes, got {}", arqc != null ? arqc.length : null);
            return new byte[0];
        }
        byte[] arc = arc(responseCode);
        if (arc == null) {
            log.warn("ARPC generation: response code must be 2 ASCII characters, got {}", responseCode);
            return new byte[0];
        }

        try {
            // ARQC XOR (ARC || six zero bytes): only the first two bytes change
            byte[] xored = arqc.clone();
            xored[0] ^= arc[0];
            xored[1] ^= arc[1];

            // Note: in production the session key is retrieved from the ArqcValidator context.
            // For now we use a simplified MAC — in a real implementation the session key
            // must be threaded through from the ARQC validation step.
            byte[] arpc = simpleMac(xored);
            log.debug("ARPC generated ({} bytes) for RC={}", arpc.length, responseCode);
            return arpc;
        } catch (Exception e) {
            log.error("ARPC generation failed: {}", e.getMessage());
            return new byte[0];
        }
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

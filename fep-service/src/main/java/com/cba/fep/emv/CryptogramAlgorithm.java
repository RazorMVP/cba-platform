package com.cba.fep.emv;

/**
 * Cryptogram algorithm used by a card scheme for ARQC/ARPC computation.
 *
 * <p>Different card schemes — and even different card products within a scheme —
 * use different symmetric cipher algorithms for their EMV session key derivation
 * and CBC-MAC cryptogram computation.
 *
 * <ul>
 *   <li>{@link #TDES} — Triple-DES (3DES/DESede); used by Visa, Mastercard, Verve, Afrigo,
 *       and UnionPay international cards (outside mainland China)</li>
 *   <li>{@link #SM4} — China national standard symmetric cipher (128-bit block, 128-bit key);
 *       used by domestic China UnionPay (QPBOC) cards issued in mainland China</li>
 * </ul>
 *
 * <p>The algorithm is determined by the {@link com.cba.fep.scheme.SchemeAdapter} for the
 * BIN range in question and passed to {@link ArqcValidator} at validation time.
 */
public enum CryptogramAlgorithm {

    /**
     * Triple-DES CBC-MAC (EMV Book 2 standard).
     * Session key: 16-byte double-length TDES key derived from issuer master key + ATC.
     */
    TDES,

    /**
     * SM4 CBC-MAC (PBOC 3.0 / QuickPass standard for domestic China UnionPay).
     * Session key: 16-byte SM4 key derived from issuer master key + ATC.
     * Specified in China Financial Standard JR/T 0025 and GB/T 32918.
     */
    SM4
}

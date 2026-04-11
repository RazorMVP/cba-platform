package com.cba.fep.hsm;

/**
 * HSM (Hardware Security Module) adapter interface.
 *
 * <p>Abstracts over two implementations:
 * <ul>
 *   <li>{@link SoftwareHsmAdapter} — Bouncy Castle TDES; for dev and testing only</li>
 *   <li>{@link ThalesPayShieldAdapter} — Thales payShield 9000/10K command protocol; for production</li>
 * </ul>
 *
 * <p>Thales payShield command codes used by the FEP:
 * <pre>
 *   CW — Generate/Verify Card Verification Value (CVV/CVV2/iCVV)
 *   DC — Verify PIN using Visa PVV method
 *   CA — Verify PIN using IBM 3624 offset method
 *   NC — Generate MAC (Message Authentication Code)
 *   KQ — Generate key under LMK
 *   A2 — Verify PIN offset (Interswitch / Verve)
 *   BU — Generate ARQC cryptogram (EMV; used by SoftwareHsmAdapter only)
 *   CY — Generate ARPC
 * </pre>
 */
public interface HsmAdapter {

    /**
     * Verify a PIN block (ISO 9564-1 Format 0 / 3DES encrypted).
     *
     * @param pinBlock encrypted PIN block from DE52
     * @param pan      primary account number (used as PIN block key in ISO Format 0)
     * @return {@code true} if PIN is correct
     */
    boolean verifyPin(byte[] pinBlock, String pan);

    /**
     * Verify a Card Verification Value (CVV / CVV2 / iCVV).
     *
     * @param pan        primary account number
     * @param expiryDate expiry date (YYMM)
     * @param serviceCode service code (3 digits; '000' for CVV2)
     * @param cvv        CVV value from DE48 or track data
     * @return {@code true} if CVV is correct
     */
    boolean verifyCvv(String pan, String expiryDate, String serviceCode, String cvv);

    /**
     * Generate a MAC (Message Authentication Code) for a message.
     *
     * @param data     message data bytes to authenticate
     * @param keyIndex index of the MAC key in the HSM key store
     * @return 8-byte MAC
     */
    byte[] generateMac(byte[] data, int keyIndex);

    /**
     * Verify a MAC against expected value.
     *
     * @param data     message data bytes that were authenticated
     * @param mac      received MAC from DE64 or DE128
     * @param keyIndex index of the MAC key in the HSM key store
     * @return {@code true} if MAC is valid
     */
    boolean verifyMac(byte[] data, byte[] mac, int keyIndex);

    /**
     * Translate a PIN block from one encryption key to another.
     * Used when routing transactions across different key zones.
     *
     * @param pinBlock encrypted PIN block under source key
     * @param pan      primary account number
     * @return PIN block re-encrypted under the destination zone key
     */
    byte[] translatePinBlock(byte[] pinBlock, String pan);

    /**
     * Generate a random session key under the LMK.
     *
     * @return key check value (KCV) of the generated key
     */
    byte[] generateSessionKey();
}

package com.cba.card.threeds;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * Software CAVV (Cardholder Authentication Verification Value) generator.
 *
 * <p><strong>Dev/test only.</strong> In production, CAVV generation MUST go
 * through an HSM (Thales payShield or equivalent) using a PCI-compliant
 * key hierarchy. This implementation uses standard JDK HMAC-SHA256.
 *
 * <h3>Algorithm</h3>
 * <pre>
 * cardKey = HMAC-SHA256(masterKey, cardId)            // 32-byte derived key
 * input   = acsTransId + amount12 + currency3 + eci2  // deterministic input
 * raw     = HMAC-SHA256(cardKey, input)                // 32 bytes
 * cavv    = Base64(raw[0..19])                         // 20 bytes → 28-char Base64
 * </pre>
 *
 * <p>The CAVV cryptographically binds the authentication event to the
 * specific card and transaction parameters. It is verified by the issuer's
 * authorization system when the CAVV arrives in DE 55 of the ISO 8583 message.
 */
@Slf4j
@Component
public class CavvGenerator {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int CAVV_BYTES = 20;

    @Value("${card.threeds.cavv-master-key:dev-cavv-master-key-change-in-prod}")
    private String masterKey;

    /**
     * Generate a CAVV for a successfully authenticated 3DS session.
     *
     * @param cardId     UUID of the authenticating card
     * @param acsTransId ACS Transaction ID for this session
     * @param amount     Transaction amount (may be null for non-purchase flows)
     * @param currency   ISO 4217 currency code (e.g. "840")
     * @param eci        ECI indicator ("05" or "06")
     * @return Base64-encoded CAVV (28 characters)
     */
    public String generate(UUID cardId, UUID acsTransId,
                           BigDecimal amount, String currency, String eci) {
        try {
            // Step 1 — Derive a per-card key from the master key + cardId
            byte[] cardKey = hmac(
                    masterKey.getBytes(StandardCharsets.UTF_8),
                    cardId.toString().getBytes(StandardCharsets.UTF_8));

            // Step 2 — Build deterministic input string
            String amountStr = amount != null
                    ? String.format("%012d", amount.movePointRight(2).longValue())
                    : "000000000000";
            if (currency == null || currency.isBlank()) {
                throw new IllegalArgumentException(
                        "Currency code is required for CAVV generation — cannot default to USD");
            }
            String currencyStr = String.format("%-3s", currency);
            String eciStr      = eci != null ? eci : "06";
            String input = acsTransId.toString() + amountStr + currencyStr + eciStr;

            // Step 3 — Compute CAVV as first 20 bytes of HMAC-SHA256(cardKey, input)
            byte[] raw = hmac(cardKey, input.getBytes(StandardCharsets.UTF_8));
            byte[] cavvBytes = Arrays.copyOf(raw, CAVV_BYTES);

            return Base64.getEncoder().encodeToString(cavvBytes);

        } catch (Exception e) {
            log.error("CAVV generation failed for card {}: {}", cardId, e.getMessage());
            throw new IllegalStateException("CAVV generation failed", e);
        }
    }

    /**
     * Hash a plaintext value with HMAC-SHA256 using the given key.
     * Used by {@link ThreeDsService} for OTP hash derivation.
     */
    public String hmacHex(byte[] key, String value) {
        try {
            byte[] raw = hmac(key, value.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(key, HMAC_ALGO));
        return mac.doFinal(data);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}

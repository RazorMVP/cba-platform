package com.cba.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Verifies the external payments gateway's callback signature (backlog 1):
 * {@code X-Gateway-Signature: sha256=<hex>} = HMAC-SHA256(secret, {@code X-Gateway-Timestamp} + "." + raw body),
 * with the timestamp (epoch seconds) within {@link #MAX_SKEW} of now so a captured callback can't be
 * replayed later. Compared in constant time.
 */
public final class CallbackSignature {

    public static final Duration MAX_SKEW = Duration.ofMinutes(5);

    public enum Verdict { OK, MISSING, STALE, MISMATCH }

    private CallbackSignature() {}

    public static Verdict verify(String secret, String timestampHeader, String signatureHeader, byte[] body, Instant now) {
        if (timestampHeader == null || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return Verdict.MISSING;
        }
        long epoch;
        try {
            epoch = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return Verdict.MISSING;
        }
        if (Duration.between(Instant.ofEpochSecond(epoch), now).abs().compareTo(MAX_SKEW) > 0) {
            return Verdict.STALE;
        }
        byte[] expected = sign(secret, timestampHeader.trim(), body).getBytes(StandardCharsets.US_ASCII);
        byte[] given = signatureHeader.trim().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given) ? Verdict.OK : Verdict.MISMATCH;
    }

    /** {@code sha256=<hex>} for the timestamp and body — what the gateway must send. */
    public static String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}

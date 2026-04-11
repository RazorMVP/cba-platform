package com.cba.card.terminal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal ISO 8583-1987 message builder for the terminal simulator.
 *
 * <p>Builds LLVAR/fixed-length DE fields using a simple byte-array approach —
 * no jPOS dependency needed in card-service (jPOS lives in fep-service).
 *
 * <p>Supported MTIs: 0100 (authorization), 0200 (financial), 0400 (reversal),
 * 0800 (network management).
 *
 * <p>Fields are encoded per ISO 8583-1987 conventions:
 * <ul>
 *   <li>Numeric/fixed: right-justified, zero-padded, ASCII</li>
 *   <li>LLVAR (DE 2, 35, 43): 2-digit ASCII length prefix + ASCII value</li>
 *   <li>Binary/hex (DE 52, 55): raw bytes</li>
 *   <li>Bitmap: 8-byte primary bitmap (no secondary for these DEs)</li>
 * </ul>
 */
public class Iso8583Builder {

    // DE usage: 2,3,4,7,11,12,13,14,18,22,37,39,41,42,43,49
    // These all fit in the primary bitmap (DEs 1-64).

    private static final AtomicInteger STAN_COUNTER = new AtomicInteger(100000);

    private final StringBuilder body = new StringBuilder();
    private final BitSet bitmap = new BitSet(64);
    private final String mti;

    // Field buffers (indexed 1-64, we only use a subset)
    private final String[] fields = new String[65];

    public Iso8583Builder(String mti) {
        this.mti = mti; // e.g. "0100"
    }

    /** Returns a 6-digit STAN, auto-incrementing and wrapping at 999999. */
    public static String nextStan() {
        int n = STAN_COUNTER.incrementAndGet();
        if (n > 999999) {
            STAN_COUNTER.compareAndSet(n, 100000);
            n = 100000;
        }
        return String.format("%06d", n);
    }

    /** Sets a field value and marks its bit in the bitmap. */
    public Iso8583Builder set(int de, String value) {
        fields[de] = value;
        bitmap.set(de - 1); // BitSet is 0-indexed; DE1=bit0
        return this;
    }

    /**
     * Builds the complete ISO 8583 message as raw bytes.
     *
     * <p>Layout: [4 bytes MTI ASCII] [8 bytes primary bitmap] [DE fields...]
     */
    public byte[] build() {
        StringBuilder out = new StringBuilder();

        // MTI (4 ASCII digits)
        out.append(mti);

        // Primary bitmap (8 bytes = 64 bits, hex-encoded as 16 ASCII chars for simplicity)
        // We'll use a binary bitmap (8 raw bytes)
        byte[] bitmapBytes = toByteArray(bitmap);
        // Append bitmap as raw bytes — need to build as byte array overall

        // Build field data as ASCII string first, then concat
        StringBuilder fieldData = new StringBuilder();
        for (int de = 2; de <= 64; de++) {
            if (bitmap.get(de - 1) && fields[de] != null) {
                fieldData.append(fields[de]);
            }
        }

        // Assemble: 4 bytes MTI + 8 bytes bitmap + field bytes
        byte[] mtiBytes     = mti.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] fieldBytes   = fieldData.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        byte[] result = new byte[4 + 8 + fieldBytes.length];
        System.arraycopy(mtiBytes, 0, result, 0, 4);
        System.arraycopy(bitmapBytes, 0, result, 4, 8);
        System.arraycopy(fieldBytes, 0, result, 12, fieldBytes.length);
        return result;
    }

    /** Returns the current MTI. */
    public String getMti() { return mti; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static byte[] toByteArray(BitSet bits) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 64; i++) {
            if (bits.get(i)) {
                bytes[i / 8] |= (byte) (0x80 >> (i % 8));
            }
        }
        return bytes;
    }

    /** Formats amount to 12-digit numeric string (minor units). */
    public static String formatAmount(BigDecimal amount) {
        if (amount == null) return "000000000000";
        long minor = amount.movePointRight(2).longValue();
        return String.format("%012d", minor);
    }

    /** Formats DE7 Transmission Date Time (MMDDHHmmss). */
    public static String transmissionDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
    }

    /** Formats DE12 Local Transaction Time (HHmmss). */
    public static String localTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
    }

    /** Formats DE13 Local Transaction Date (MMDD). */
    public static String localDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd"));
    }

    /** Generates a 12-character RRN. */
    public static String generateRrn() {
        return String.format("%012d",
                System.currentTimeMillis() % 1_000_000_000_000L);
    }

    /** Wraps value as LLVAR (2-digit ASCII length + value). */
    public static String llvar(String value) {
        return String.format("%02d", value.length()) + value;
    }
}

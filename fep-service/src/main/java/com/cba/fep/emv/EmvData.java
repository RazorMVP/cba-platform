package com.cba.fep.emv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds parsed EMV TLV data from DE55.
 *
 * <p>Tags are stored as hex strings (uppercase, no spaces).
 * Values are stored as raw byte arrays.
 *
 * <p>Example usage:
 * <pre>
 *   EmvData emv = emvDataParser.parse(iccBytes);
 *   byte[] arqc = emv.getTag(EmvTag.ARQC);
 *   byte[] atc  = emv.getTag(EmvTag.APPLICATION_TRANSACTION_COUNTER);
 * </pre>
 */
public record EmvData(Map<String, byte[]> tags) {

    public EmvData {
        tags = Collections.unmodifiableMap(new HashMap<>(tags));
    }

    /**
     * Get the raw bytes for a tag, or {@code null} if not present.
     *
     * @param tag hex tag string (e.g., "9F26")
     */
    public byte[] getTag(String tag) {
        return tags.get(tag.toUpperCase());
    }

    public boolean hasTag(String tag) {
        return tags.containsKey(tag.toUpperCase());
    }

    /**
     * Get tag value as a hex string, or {@code null} if not present.
     */
    public String getTagHex(String tag) {
        byte[] value = getTag(tag);
        if (value == null) return null;
        StringBuilder sb = new StringBuilder(value.length * 2);
        for (byte b : value) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    /**
     * Get tag value as a numeric string (for numeric-coded BCD fields like ATC, currency code).
     */
    public String getTagNumeric(String tag) {
        return getTagHex(tag);
    }

    public int size() {
        return tags.size();
    }

    @Override
    public String toString() {
        return "EmvData{tags=" + tags.keySet() + "}";
    }
}

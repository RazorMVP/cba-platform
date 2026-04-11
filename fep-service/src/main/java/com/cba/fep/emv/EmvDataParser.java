package com.cba.fep.emv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * BER-TLV parser for EMV DE55 (ICC Data).
 *
 * <p>Parses the raw bytes from ISO 8583 DE55 into a flat map of
 * tag → value. Constructed tags (template tags like 70, 77, 80) are
 * unwrapped — their content is flattened into the same map.
 *
 * <p>Handles:
 * <ul>
 *   <li>1-byte and 2-byte tags (BER-TLV multi-byte tag encoding)</li>
 *   <li>1-byte and 2-byte lengths (BER long-form length encoding)</li>
 *   <li>Constructed tags (bit 6 of first byte = 1) — recursively parsed</li>
 * </ul>
 *
 * <p>Does NOT handle:
 * <ul>
 *   <li>Indefinite-length encoding (not used in EMV)</li>
 *   <li>3+ byte lengths (max EMV field is 999 bytes)</li>
 * </ul>
 */
@Slf4j
@Component
public class EmvDataParser {

    /**
     * Parse raw DE55 bytes into an {@link EmvData} object.
     *
     * @param iccData raw bytes from DE55
     * @return parsed EMV tags; empty if input is null or malformed
     */
    public EmvData parse(byte[] iccData) {
        if (iccData == null || iccData.length == 0) {
            return new EmvData(Map.of());
        }
        Map<String, byte[]> tags = new HashMap<>();
        parseTlv(iccData, 0, iccData.length, tags);
        log.debug("EMV parse: {} tags extracted from {} bytes", tags.size(), iccData.length);
        return new EmvData(tags);
    }

    private int parseTlv(byte[] data, int offset, int end, Map<String, byte[]> out) {
        int pos = offset;
        while (pos < end) {
            if (pos >= data.length) break;

            // --- Parse tag ---
            int tagStart = pos;
            boolean isConstructed = (data[pos] & 0x20) != 0;
            String tag;

            if ((data[pos] & 0x1F) == 0x1F) {
                // Multi-byte tag: first byte has 0x1F in low 5 bits
                if (pos + 1 >= data.length) break;
                tag = String.format("%02X%02X", data[pos] & 0xFF, data[pos + 1] & 0xFF);
                pos += 2;
            } else {
                tag = String.format("%02X", data[pos] & 0xFF);
                pos++;
            }

            if (pos >= end) break;

            // --- Parse length ---
            int len;
            if ((data[pos] & 0x80) == 0) {
                // Short form: length in low 7 bits
                len = data[pos] & 0x7F;
                pos++;
            } else {
                // Long form: next N bytes carry the length value
                int numLenBytes = data[pos] & 0x7F;
                pos++;
                if (numLenBytes > 2 || pos + numLenBytes > end) break;
                len = 0;
                for (int i = 0; i < numLenBytes; i++) {
                    len = (len << 8) | (data[pos++] & 0xFF);
                }
            }

            if (pos + len > end) {
                log.warn("EMV TLV: tag {} length {} overflows buffer at pos {}", tag, len, pos);
                break;
            }

            // --- Extract value ---
            byte[] value = new byte[len];
            System.arraycopy(data, pos, value, 0, len);

            if (isConstructed) {
                // Recurse into constructed tags (unwrap templates)
                parseTlv(data, pos, pos + len, out);
            } else {
                out.put(tag.toUpperCase(), value);
            }

            pos += len;
        }
        return pos;
    }
}

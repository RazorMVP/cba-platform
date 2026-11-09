package com.cba.fep.emv.tlv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * BER-TLV decoder for EMV ICC data (DE55), per EMV 4.4 Book 3 Annex B and ISO/IEC 8825-1.
 *
 * <ul>
 *   <li><b>Tag:</b> one byte, or — when its low five bits are {@code 11111} — further bytes for as long as
 *       b8 of the previous byte is set ("another byte follows"). Three-byte tags such as {@code DF8101}
 *       (EMV kernel tags) are read whole. More than 4 tag bytes is rejected.</li>
 *   <li><b>Length:</b> short form ({@code 00}–{@code 7F}), or long form {@code 81 NN} / {@code 82 NN NN}.
 *       EMV allows at most two subsequent length bytes, so {@code 83}+ is rejected, and the indefinite
 *       form {@code 80} is not used in EMV — rejected too.</li>
 *   <li><b>Padding:</b> {@code 00} and {@code FF} bytes before, between and after data objects carry no
 *       meaning (Annex B1; ISO/IEC 7816-4 §5.2.2) and are skipped, at every nesting level.</li>
 *   <li><b>Constructed objects</b> (b6 of the first tag byte) are decoded into children; the value is
 *       kept as well.</li>
 * </ul>
 *
 * <p>Order and duplicates are preserved — deciding which occurrence wins is the caller's business.
 * Anything that isn't well formed (truncated tag, length or value) throws {@link TlvParseException};
 * nothing is returned half-parsed.
 */
public final class BerTlvParser {

    /** EMV uses up to 3-byte tags; ISO/IEC 8825-1 allows more, 4 is a generous ceiling. */
    static final int MAX_TAG_BYTES = 4;
    /** Templates nest a few levels (70/77 → 71/72 …); anything deeper is not ICC data. */
    static final int MAX_DEPTH = 16;

    private BerTlvParser() {}

    /**
     * Decode a buffer of concatenated BER-TLV objects.
     *
     * @param data raw bytes; {@code null} or empty gives an empty list
     * @return the top-level objects in order (constructed ones with their children)
     * @throws TlvParseException if the data is not well-formed BER-TLV
     */
    public static List<Tlv> parse(byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        return parse(data, 0, data.length, 0);
    }

    private static List<Tlv> parse(byte[] data, int from, int to, int depth) {
        if (depth > MAX_DEPTH) {
            throw new TlvParseException("TLV nesting deeper than " + MAX_DEPTH + " levels");
        }
        List<Tlv> out = new ArrayList<>();
        int pos = from;
        while (pos < to) {
            int b = data[pos] & 0xFF;
            if (b == 0x00 || b == 0xFF) {        // padding between objects
                pos++;
                continue;
            }

            // --- Tag ---
            int tagStart = pos++;
            if ((b & 0x1F) == 0x1F) {
                int next;
                do {
                    if (pos >= to) {
                        throw new TlvParseException("Truncated tag at offset " + tagStart);
                    }
                    next = data[pos++] & 0xFF;
                    if (pos - tagStart > MAX_TAG_BYTES) {
                        throw new TlvParseException("Tag at offset " + tagStart + " longer than "
                                + MAX_TAG_BYTES + " bytes");
                    }
                } while ((next & 0x80) != 0);
            }
            String tag = HexFormat.of().withUpperCase().formatHex(data, tagStart, pos);

            // --- Length ---
            if (pos >= to) {
                throw new TlvParseException("Missing length for tag " + tag);
            }
            int first = data[pos++] & 0xFF;
            int len;
            if (first < 0x80) {
                len = first;
            } else if (first == 0x81 || first == 0x82) {
                int n = first & 0x7F;
                if (pos + n > to) {
                    throw new TlvParseException("Truncated length for tag " + tag);
                }
                len = 0;
                for (int i = 0; i < n; i++) {
                    len = (len << 8) | (data[pos++] & 0xFF);
                }
            } else {
                throw new TlvParseException(String.format(
                        "Unsupported length byte %02X for tag %s (EMV: short form, 81 or 82)", first, tag));
            }

            // --- Value ---
            if (len > to - pos) {
                throw new TlvParseException("Value of tag " + tag + " (" + len + " bytes) runs past the end");
            }
            byte[] value = Arrays.copyOfRange(data, pos, pos + len);
            List<Tlv> children = Tlv.isConstructedTag(tag)
                    ? parse(data, pos, pos + len, depth + 1)
                    : List.of();
            out.add(new Tlv(tag, value, children));
            pos += len;
        }
        return out;
    }
}

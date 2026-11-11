package com.cba.fep.emv.tlv;

import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * BER-TLV encoder for EMV ICC data (DE55), the counterpart of {@link BerTlvParser}.
 *
 * <p>Lengths use the minimal form (EMV Book 3 Annex B2): short form below 128, {@code 81 NN} below 256,
 * {@code 82 NN NN} below 65,536; longer values are refused. A constructed object is encoded from its
 * children, so issuer scripts ({@code 71}/{@code 72} holding {@code 9F18} and {@code 86} commands) can be
 * nested inside a response template.
 */
public final class BerTlvBuilder {

    private BerTlvBuilder() {}

    /**
     * Encode one primitive data object.
     *
     * @param tagHex tag in hex ("91", "9F26", "DF8101")
     * @param value  value bytes ({@code null} = empty)
     * @throws IllegalArgumentException for a malformed tag or a value of 65,536 bytes or more
     */
    public static byte[] encode(String tagHex, byte[] value) {
        byte[] tag = tagBytes(tagHex);
        byte[] v = value == null ? new byte[0] : value;
        ByteArrayOutputStream out = new ByteArrayOutputStream(tag.length + 3 + v.length);
        out.writeBytes(tag);
        out.writeBytes(lengthBytes(v.length));
        out.writeBytes(v);
        return out.toByteArray();
    }

    /** Encode a data object; a constructed one with children is encoded from them, recursively. */
    public static byte[] encode(Tlv tlv) {
        byte[] value = tlv.children().isEmpty() ? tlv.value() : encodeAll(tlv.children());
        return encode(tlv.tag(), value);
    }

    /** Encode several data objects back to back, in order. */
    public static byte[] encodeAll(List<Tlv> objects) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (objects != null) {
            for (Tlv t : objects) {
                out.writeBytes(encode(t));
            }
        }
        return out.toByteArray();
    }

    /**
     * Validate a tag per ISO/IEC 8825-1: one byte whose low five bits aren't {@code 11111}; or a first byte
     * with {@code 11111}, then bytes with b8 set ("more follows") ending in one with b8 clear. 1–4 bytes;
     * {@code 00} is padding, not a tag.
     */
    static byte[] tagBytes(String tagHex) {
        if (tagHex == null || tagHex.isEmpty() || tagHex.length() % 2 != 0) {
            throw new IllegalArgumentException("Tag must be an even number of hex digits: " + tagHex);
        }
        byte[] tag;
        try {
            tag = HexFormat.of().parseHex(tagHex.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tag is not hex: " + tagHex, e);
        }
        if (tag.length > BerTlvParser.MAX_TAG_BYTES) {
            throw new IllegalArgumentException("Tag longer than " + BerTlvParser.MAX_TAG_BYTES + " bytes: " + tagHex);
        }
        int first = tag[0] & 0xFF;
        if (first == 0x00) {
            throw new IllegalArgumentException("00 is padding, not a tag");
        }
        boolean multiByte = (first & 0x1F) == 0x1F;
        if (!multiByte) {
            if (tag.length != 1) {
                throw new IllegalArgumentException("Tag " + tagHex + ": first byte announces a one-byte tag");
            }
            return tag;
        }
        if (tag.length == 1) {
            throw new IllegalArgumentException("Tag " + tagHex + ": first byte announces further tag bytes");
        }
        for (int i = 1; i < tag.length; i++) {
            boolean more = (tag[i] & 0x80) != 0;
            boolean last = i == tag.length - 1;
            if (more == last) {
                throw new IllegalArgumentException("Tag " + tagHex + ": byte " + (i + 1)
                        + (last ? " says more bytes follow" : " ends the tag early"));
            }
        }
        return tag;
    }

    static byte[] lengthBytes(int len) {
        if (len < 0x80) {
            return new byte[]{(byte) len};
        }
        if (len < 0x100) {
            return new byte[]{(byte) 0x81, (byte) len};
        }
        if (len < 0x10000) {
            return new byte[]{(byte) 0x82, (byte) (len >> 8), (byte) len};
        }
        throw new IllegalArgumentException("Value of " + len + " bytes is too long for EMV (max 65535)");
    }
}

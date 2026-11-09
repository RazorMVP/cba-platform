package com.cba.fep.emv.tlv;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One BER-TLV data object (EMV Book 3 Annex B; ISO/IEC 8825-1).
 *
 * <p>{@code tag} is the full tag in uppercase hex ("9F26", "DF8101"). {@code value} is the raw value
 * field; for a constructed object it is the encoding of {@code children}. A primitive object has no
 * children. Arrays and lists are copied in and out, so a {@code Tlv} is immutable.
 */
public record Tlv(String tag, byte[] value, List<Tlv> children) {

    public Tlv {
        Objects.requireNonNull(tag, "tag");
        tag = tag.toUpperCase(Locale.ROOT);
        value = value == null ? new byte[0] : value.clone();
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** A primitive data object. */
    public static Tlv primitive(String tag, byte[] value) {
        return new Tlv(tag, value, List.of());
    }

    /**
     * A constructed data object (template) whose value is the encoding of {@code children} in order.
     *
     * @throws IllegalArgumentException if {@code tag} is not a constructed tag (b6 of the first byte)
     */
    public static Tlv constructed(String tag, List<Tlv> children) {
        if (!isConstructedTag(tag)) {
            throw new IllegalArgumentException("Tag " + tag + " is not a constructed tag");
        }
        return new Tlv(tag, BerTlvBuilder.encodeAll(children), children);
    }

    @Override
    public byte[] value() {
        return value.clone();
    }

    /** True when b6 (0x20) of the tag's first byte is set — the value holds further TLV objects. */
    public boolean constructed() {
        return isConstructedTag(tag);
    }

    static boolean isConstructedTag(String tag) {
        return (HexFormat.fromHexDigits(tag, 0, 2) & 0x20) != 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Tlv other
                && tag.equals(other.tag)
                && Arrays.equals(value, other.value)
                && children.equals(other.children);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * tag.hashCode() + Arrays.hashCode(value)) + children.hashCode();
    }

    @Override
    public String toString() {
        return children.isEmpty()
                ? tag + "[" + value.length + "]"
                : tag + children;
    }
}

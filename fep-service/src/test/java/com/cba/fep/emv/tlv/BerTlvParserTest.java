package com.cba.fep.emv.tlv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BerTlvParser} against EMV Book 3 Annex B: tag and length forms, padding, nesting, and every way
 * a truncated buffer must be refused rather than half-read.
 */
class BerTlvParserTest {

    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s.replace(" ", ""));
    }

    private static String hexOf(byte[] b) {
        return HexFormat.of().withUpperCase().formatHex(b);
    }

    @Test
    @DisplayName("null and empty input give an empty list")
    void emptyInput() {
        assertThat(BerTlvParser.parse(null)).isEmpty();
        assertThat(BerTlvParser.parse(new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("one-byte tag, short-form length")
    void oneByteTag() {
        List<Tlv> out = BerTlvParser.parse(hex("95 05 0000008000"));
        assertThat(out).singleElement().satisfies(t -> {
            assertThat(t.tag()).isEqualTo("95");
            assertThat(hexOf(t.value())).isEqualTo("0000008000");
            assertThat(t.constructed()).isFalse();
            assertThat(t.children()).isEmpty();
        });
    }

    @Test
    @DisplayName("two-byte tag (9F26)")
    void twoByteTag() {
        Tlv t = BerTlvParser.parse(hex("9F26 08 1122334455667788")).get(0);
        assertThat(t.tag()).isEqualTo("9F26");
        assertThat(hexOf(t.value())).isEqualTo("1122334455667788");
    }

    @Test
    @DisplayName("three-byte tag (DF8101): b8 of the second byte says another byte follows")
    void threeByteTag() {
        List<Tlv> out = BerTlvParser.parse(hex("DF8101 02 ABCD 9F27 01 80"));
        assertThat(out).extracting(Tlv::tag).containsExactly("DF8101", "9F27");
        assertThat(hexOf(out.get(0).value())).isEqualTo("ABCD");
    }

    @Test
    @DisplayName("a tag of more than 4 bytes is refused")
    void overLongTag() {
        assertThatThrownBy(() -> BerTlvParser.parse(hex("DF 81 81 81 01 01 00")))
                .isInstanceOf(TlvParseException.class)
                .hasMessageContaining("longer than 4 bytes");
    }

    @Test
    @DisplayName("long-form length 81 NN")
    void length81() {
        String value = "AB".repeat(200);
        Tlv t = BerTlvParser.parse(hex("9F10 81 C8 " + value)).get(0);
        assertThat(t.value()).hasSize(200);
    }

    @Test
    @DisplayName("long-form length 82 NN NN")
    void length82() {
        String value = "CD".repeat(300);
        Tlv t = BerTlvParser.parse(hex("9F10 82 012C " + value)).get(0);
        assertThat(t.value()).hasSize(300);
    }

    @Test
    @DisplayName("length 83 (three subsequent bytes) is refused — EMV allows at most two")
    void length83Refused() {
        assertThatThrownBy(() -> BerTlvParser.parse(hex("95 83 000005 0000008000")))
                .isInstanceOf(TlvParseException.class)
                .hasMessageContaining("83");
    }

    @Test
    @DisplayName("indefinite length 80 is refused")
    void indefiniteLengthRefused() {
        assertThatThrownBy(() -> BerTlvParser.parse(hex("70 80 95 01 00 0000")))
                .isInstanceOf(TlvParseException.class)
                .hasMessageContaining("80");
    }

    @Test
    @DisplayName("00 and FF padding is skipped before, between and after objects")
    void paddingSkipped() {
        List<Tlv> out = BerTlvParser.parse(hex("00 00 FF 9F27 01 80 00 FF 9F36 02 001C FF 00 00"));
        assertThat(out).extracting(Tlv::tag).containsExactly("9F27", "9F36");
    }

    @Test
    @DisplayName("padding inside a template is skipped too")
    void paddingInsideTemplate() {
        Tlv t = BerTlvParser.parse(hex("77 07 00 9F27 01 80 FF 00")).get(0);
        assertThat(t.children()).extracting(Tlv::tag).containsExactly("9F27");
    }

    @Test
    @DisplayName("nested templates 70 → 77 → primitive are decoded into children")
    void nestedTemplates() {
        Tlv outer = BerTlvParser.parse(hex("70 11 77 0F 9F26 08 1122334455667788 9F27 01 80")).get(0);
        assertThat(outer.tag()).isEqualTo("70");
        assertThat(outer.constructed()).isTrue();
        Tlv inner = outer.children().get(0);
        assertThat(inner.tag()).isEqualTo("77");
        assertThat(inner.children()).extracting(Tlv::tag).containsExactly("9F26", "9F27");
        assertThat(hexOf(outer.value())).isEqualTo("770F9F260811223344556677889F270180");
    }

    @Test
    @DisplayName("issuer script template 71 holds its script identifier and command")
    void issuerScriptTemplate() {
        Tlv script = BerTlvParser.parse(hex("71 0F 9F18 04 00000001 86 06 8424000002AB")).get(0);
        assertThat(script.constructed()).isTrue();
        assertThat(script.children()).extracting(Tlv::tag).containsExactly("9F18", "86");
        assertThat(hexOf(script.children().get(1).value())).isEqualTo("8424000002AB");
    }

    @Test
    @DisplayName("duplicates are kept, in order")
    void duplicatesPreserved() {
        List<Tlv> out = BerTlvParser.parse(hex("9F26 01 01 95 01 02 9F26 01 03"));
        assertThat(out).extracting(Tlv::tag).containsExactly("9F26", "95", "9F26");
        assertThat(out.get(0).value()).containsExactly(1);
        assertThat(out.get(2).value()).containsExactly(3);
    }

    @Test
    @DisplayName("a value running past the end is refused")
    void truncatedValue() {
        assertThatThrownBy(() -> BerTlvParser.parse(hex("82 02 AABB 95 05 01")))
                .isInstanceOf(TlvParseException.class)
                .hasMessageContaining("95");
    }

    @Test
    @DisplayName("a template whose child runs past the template's end is refused")
    void truncatedChild() {
        assertThatThrownBy(() -> BerTlvParser.parse(hex("77 03 9F27 05 80 8080")))
                .isInstanceOf(TlvParseException.class);
    }

    @Test
    @DisplayName("a multi-byte tag cut off at the end of the buffer is refused")
    void truncatedTag() {
        assertThatThrownBy(() -> BerTlvParser.parse(hex("95 01 00 9F")))
                .isInstanceOf(TlvParseException.class)
                .hasMessageContaining("Truncated tag");
        assertThatThrownBy(() -> BerTlvParser.parse(hex("DF 81")))
                .isInstanceOf(TlvParseException.class);
    }

    @Test
    @DisplayName("a tag with no length, or a long-form length cut off, is refused")
    void truncatedLength() {
        assertThatThrownBy(() -> BerTlvParser.parse(hex("9F26")))
                .isInstanceOf(TlvParseException.class);
        assertThatThrownBy(() -> BerTlvParser.parse(hex("9F10 82 01")))
                .isInstanceOf(TlvParseException.class);
    }

    @Test
    @DisplayName("nesting deeper than the limit is refused rather than recursing without bound")
    void excessiveNesting() {
        // 20 levels of 70 templates around a 1-byte primitive
        byte[] inner = hex("95 01 00");
        for (int i = 0; i < 20; i++) {
            inner = BerTlvBuilder.encode("70", inner);
        }
        byte[] data = inner;
        assertThatThrownBy(() -> BerTlvParser.parse(data))
                .isInstanceOf(TlvParseException.class)
                .hasMessageContaining("nesting");
    }
}

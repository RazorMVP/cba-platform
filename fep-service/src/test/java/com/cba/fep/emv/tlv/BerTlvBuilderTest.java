package com.cba.fep.emv.tlv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link BerTlvBuilder}: minimal length encoding, tag validation, and nested templates that parse back equal. */
class BerTlvBuilderTest {

    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s.replace(" ", ""));
    }

    private static String hexOf(byte[] b) {
        return HexFormat.of().withUpperCase().formatHex(b);
    }

    @Test
    @DisplayName("tag 91 with ARPC || ARC encodes as 91 0A <10 bytes>")
    void issuerAuthenticationData() {
        byte[] out = BerTlvBuilder.encode("91", hex("1122334455667788 3030"));
        assertThat(hexOf(out)).isEqualTo("910A11223344556677883030");
    }

    @Test
    @DisplayName("short form up to 127 bytes, 81 from 128 to 255, 82 from 256")
    void minimalLengths() {
        assertThat(hexOf(BerTlvBuilder.encode("9F10", new byte[127])).substring(0, 6)).isEqualTo("9F107F");
        assertThat(hexOf(BerTlvBuilder.encode("9F10", new byte[128])).substring(0, 8)).isEqualTo("9F108180");
        assertThat(hexOf(BerTlvBuilder.encode("9F10", new byte[255])).substring(0, 8)).isEqualTo("9F1081FF");
        assertThat(hexOf(BerTlvBuilder.encode("9F10", new byte[256])).substring(0, 10)).isEqualTo("9F10820100");
        assertThat(BerTlvBuilder.encode("9F10", new byte[65_535])).hasSize(2 + 3 + 65_535);
    }

    @Test
    @DisplayName("65,536 bytes or more is refused")
    void tooLong() {
        assertThatThrownBy(() -> BerTlvBuilder.encode("9F10", new byte[65_536]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null value encodes as zero length")
    void nullValue() {
        assertThat(hexOf(BerTlvBuilder.encode("95", null))).isEqualTo("9500");
    }

    @Test
    @DisplayName("multi-byte tags (9F26, DF8101) are written whole; lower-case hex is accepted")
    void multiByteTags() {
        assertThat(hexOf(BerTlvBuilder.encode("9f26", hex("01")))).isEqualTo("9F260101");
        assertThat(hexOf(BerTlvBuilder.encode("DF8101", hex("ABCD")))).isEqualTo("DF810102ABCD");
    }

    @Test
    @DisplayName("malformed tags are refused")
    void malformedTags() {
        for (String bad : new String[]{null, "", "9", "ZZ", "00", "9F", "9F81", "5F2A00", "95 01", "DF81818101"}) {
            assertThatThrownBy(() -> BerTlvBuilder.encode(bad, new byte[0]))
                    .as("tag %s", bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("a nested template (77 holding 91 and an issuer script 72) builds and parses back equal")
    void nestedRoundTrip() {
        Tlv script = Tlv.constructed("72", List.of(
                Tlv.primitive("9F18", hex("00000001")),
                Tlv.primitive("86", hex("8424000002AB"))));
        Tlv template = Tlv.constructed("77", List.of(
                Tlv.primitive("91", hex("1122334455667788 3030")),
                script));

        byte[] encoded = BerTlvBuilder.encode(template);
        List<Tlv> parsed = BerTlvParser.parse(encoded);

        assertThat(parsed).containsExactly(template);
        assertThat(hexOf(encoded)).isEqualTo(
                "771D" + "910A11223344556677883030" + "720F" + "9F180400000001" + "86068424000002AB");
    }

    @Test
    @DisplayName("Tlv.constructed refuses a primitive tag")
    void constructedNeedsConstructedTag() {
        assertThatThrownBy(() -> Tlv.constructed("91", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Tlv copies its value in and out")
    void tlvIsImmutable() {
        byte[] v = hex("0102");
        Tlv t = Tlv.primitive("95", v);
        v[0] = 9;
        t.value()[1] = 9;
        assertThat(t.value()).containsExactly(1, 2);
    }
}

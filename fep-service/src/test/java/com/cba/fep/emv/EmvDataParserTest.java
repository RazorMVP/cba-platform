package com.cba.fep.emv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for {@link EmvDataParser} — the BER-TLV parser that every
 * chip-card transaction's DE55 flows through. A bug here means cryptogram tags
 * (ARQC, ATC, CID) are read wrong, which corrupts authentication for real money.
 */
class EmvDataParserTest {

    private final EmvDataParser parser = new EmvDataParser();

    /** Hex string → bytes (spaces allowed for readability). */
    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s.replace(" ", ""));
    }

    @Test
    @DisplayName("null input yields an empty EmvData (never NPE)")
    void nullInputIsEmpty() {
        EmvData result = parser.parse(null);
        assertThat(result.size()).isZero();
    }

    @Test
    @DisplayName("empty input yields an empty EmvData")
    void emptyInputIsEmpty() {
        EmvData result = parser.parse(new byte[0]);
        assertThat(result.size()).isZero();
    }

    @Test
    @DisplayName("single primitive tag with short-form length is parsed")
    void singlePrimitiveTag() {
        // Tag 95 (TVR), length 5, value 00 00 00 80 00
        EmvData result = parser.parse(hex("95 05 0000008000"));
        assertThat(result.hasTag("95")).isTrue();
        assertThat(result.getTagHex("95")).isEqualTo("0000008000");
    }

    @Test
    @DisplayName("multi-byte tag (9F26) is parsed with full 4-hex key")
    void multiByteTag() {
        // Tag 9F26 (ARQC), length 8
        EmvData result = parser.parse(hex("9F26 08 1122334455667788"));
        assertThat(result.hasTag("9F26")).isTrue();
        assertThat(result.getTagHex("9F26")).isEqualTo("1122334455667788");
    }

    @Test
    @DisplayName("long-form length (0x81 NN) is decoded")
    void longFormLength() {
        // Tag 57 (Track2), long-form length 0x81 0x10 = 16 bytes
        byte[] value = hex("00112233445566778899AABBCCDDEEFF"); // 16 bytes
        EmvData result = parser.parse(hex("57 81 10 " + HexFormat.of().formatHex(value)));
        assertThat(result.getTag("57")).hasSize(16);
        assertThat(result.getTagHex("57")).isEqualTo("00112233445566778899AABBCCDDEEFF");
    }

    @Test
    @DisplayName("constructed template (tag 70) is unwrapped — children flattened, template key absent")
    void constructedTemplateIsUnwrapped() {
        // 70 (constructed template), inner = 9F26(8) + 95(5)
        //   9F26 08 <8 bytes>  -> 11 bytes
        //   95   05 <5 bytes>  ->  7 bytes
        //   inner total = 18 = 0x12
        EmvData result = parser.parse(hex(
                "70 12 "
              + "9F26 08 1122334455667788 "
              + "95 05 0000008000"));

        assertThat(result.hasTag("70")).as("template tag is unwrapped, not stored").isFalse();
        assertThat(result.getTagHex("9F26")).isEqualTo("1122334455667788");
        assertThat(result.getTagHex("95")).isEqualTo("0000008000");
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("length overflow fails closed: no tags at all, so there is no ARQC to validate")
    void overflowFailsClosed() {
        // 9F26 parsed fine, then 95 claims 255 bytes with only 1 left
        EmvData result = parser.parse(hex("9F26 08 1122334455667788 95 FF 01"));
        assertThat(result.size()).as("malformed DE55 yields no partial result").isZero();
        assertThat(result.hasTag(EmvTag.ARQC)).isFalse();
    }

    @Test
    @DisplayName("unsupported length form (83) fails closed")
    void unsupportedLengthFailsClosed() {
        assertThat(parser.parse(hex("95 83 000005 0000008000")).size()).isZero();
    }

    @Test
    @DisplayName("three-byte tag (DF8101) is read whole and the next tag is still in step")
    void threeByteTag() {
        EmvData result = parser.parse(hex("DF8101 02 ABCD 9F26 08 1122334455667788"));
        assertThat(result.getTagHex("DF8101")).isEqualTo("ABCD");
        assertThat(result.getTagHex("9F26")).isEqualTo("1122334455667788");
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("00 / FF padding before, between and after objects is skipped")
    void paddingSkipped() {
        EmvData result = parser.parse(hex("00 FF 9F27 01 80 0000 9F36 02 001C FF FF 00"));
        assertThat(result.getTagHex("9F27")).isEqualTo("80");
        assertThat(result.getTagHex("9F36")).isEqualTo("001C");
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("a duplicate tag does not replace the first occurrence (an appended 9F26 can't swap the ARQC)")
    void duplicateFirstWins() {
        EmvData result = parser.parse(hex("9F26 08 1122334455667788 9F26 08 AAAAAAAAAAAAAAAA"));
        assertThat(result.getTagHex("9F26")).isEqualTo("1122334455667788");
    }

    @Test
    @DisplayName("nested templates (70 → 77 → primitive) are descended")
    void nestedTemplates() {
        EmvData result = parser.parse(hex("70 0D 77 0B 9F26 08 1122334455667788"));
        assertThat(result.getTagHex("9F26")).isEqualTo("1122334455667788");
        assertThat(result.hasTag("70")).isFalse();
        assertThat(result.hasTag("77")).isFalse();
    }

    @Test
    @DisplayName("realistic multi-tag DE55 extracts every tag")
    void realisticDe55() {
        EmvData result = parser.parse(hex(
                "9F26 08 1122334455667788 "  // ARQC
              + "9F27 01 80 "                  // CID = ARQC
              + "9F36 02 001C "                // ATC
              + "82 02 5800 "                  // AIP
              + "9F1A 02 0840 "                // Terminal country
              + "95 05 0000000000"));         // TVR
        assertThat(result.size()).isEqualTo(6);
        assertThat(result.getTagHex(EmvTag.ARQC)).isEqualTo("1122334455667788");
        assertThat(result.getTagHex(EmvTag.CRYPTOGRAM_INFORMATION_DATA)).isEqualTo("80");
        assertThat(result.getTagHex(EmvTag.APPLICATION_TRANSACTION_COUNTER)).isEqualTo("001C");
    }

    @Test
    @DisplayName("tag lookup is case-insensitive")
    void tagLookupCaseInsensitive() {
        EmvData result = parser.parse(hex("9F26 08 1122334455667788"));
        assertThat(result.getTag("9f26")).isNotNull();
        assertThat(result.getTag("9F26")).isNotNull();
    }
}

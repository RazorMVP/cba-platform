package com.cba.fep.scheme;

import com.cba.fep.emv.EmvTag;
import com.cba.fep.emv.tlv.BerTlvParser;
import com.cba.fep.emv.tlv.Tlv;
import com.cba.fep.iso.IsoField;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AbstractSchemeAdapter#embedArpc}: the response DE55 is exactly tag 91 = ARPC || ARC (decision D8a) —
 * whatever DE55 held is replaced, and no second 9F26 is appended (the old behaviour).
 * Exercised through two concrete adapters; {@code IsoMessageFactory} isn't used by this method.
 */
class AbstractSchemeAdapterTest {

    private static final byte[] ARPC = HexFormat.of().parseHex("1122334455667788");
    private static final byte[] ARC_DECLINE = {0x30, 0x35}; // "05"

    private static ISOMsg responseEchoingRequestIcc() throws Exception {
        ISOMsg response = new ISOMsg();
        response.setMTI("0110");
        // what a clone of the request would carry
        response.set(IsoField.ICC_DATA, HexFormat.of().parseHex("9F2608AABBCCDDEEFF00119F270180"));
        return response;
    }

    private static void assertOnlyTag91(ISOMsg response) throws Exception {
        byte[] de55 = response.getBytes(IsoField.ICC_DATA);
        assertThat(HexFormat.of().withUpperCase().formatHex(de55)).isEqualTo("910A11223344556677883035");
        List<Tlv> tlvs = BerTlvParser.parse(de55);
        assertThat(tlvs).singleElement().satisfies(t -> {
            assertThat(t.tag()).isEqualTo(EmvTag.ISSUER_AUTH_DATA);
            assertThat(t.value()).hasSize(10);
        });
    }

    @Test
    @DisplayName("Visa: DE55 becomes 91 0A <ARPC||ARC>, no 9F26, request ICC data gone")
    void visaWritesOnlyTag91() throws Exception {
        ISOMsg response = responseEchoingRequestIcc();
        new VisaSchemeAdapter(null).embedArpc(response, ARPC, ARC_DECLINE);
        assertOnlyTag91(response);
    }

    @Test
    @DisplayName("UnionPay: same tag 91 layout")
    void unionPayWritesOnlyTag91() throws Exception {
        ISOMsg response = new ISOMsg();
        response.setMTI("0110");
        new UnionPaySchemeAdapter(null).embedArpc(response, ARPC, ARC_DECLINE);
        assertOnlyTag91(response);
    }

    @Test
    @DisplayName("a wrong-size ARPC or ARC is refused, nothing written")
    void wrongSizesRefused() {
        ISOMsg response = new ISOMsg();
        VisaSchemeAdapter visa = new VisaSchemeAdapter(null);
        assertThatThrownBy(() -> visa.embedArpc(response, new byte[7], ARC_DECLINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> visa.embedArpc(response, ARPC, new byte[]{0x30}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(response.hasField(IsoField.ICC_DATA)).isFalse();
    }

    @Test
    @DisplayName("Unknown scheme writes no DE55")
    void unknownSchemeWritesNothing() {
        ISOMsg response = new ISOMsg();
        new UnknownSchemeAdapter().embedArpc(response, ARPC, ARC_DECLINE);
        assertThat(response.hasField(IsoField.ICC_DATA)).isFalse();
    }
}

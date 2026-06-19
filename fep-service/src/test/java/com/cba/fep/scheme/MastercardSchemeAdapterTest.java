package com.cba.fep.scheme;

import com.cba.fep.iso.IsoField;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MastercardSchemeAdapter#extractPrivateData} — the DE48 PDS
 * (Private Data Subelements) parser. PDS format is {@code TAG(4)+LEN(3)+VALUE}
 * concatenated with no delimiters, so an off-by-one in length handling silently
 * mis-reads scheme data (3DS UCAF, tokens). The IsoMessageFactory is unused by
 * this method, so {@code null} is a safe constructor argument.
 */
class MastercardSchemeAdapterTest {

    private final MastercardSchemeAdapter adapter = new MastercardSchemeAdapter(null);

    private static ISOMsg msgWith(int field, String value) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.set(field, value);
        return msg;
    }

    @Test
    @DisplayName("a single PDS subelement is parsed by tag")
    void singlePds() throws Exception {
        // tag 0043 (UCAF), len 005, value ABCDE
        Map<String, String> data = adapter.extractPrivateData(msgWith(IsoField.ADDITIONAL_DATA_PRIVATE, "0043005ABCDE"));
        assertThat(data).containsEntry("mc.pds.0043", "ABCDE");
    }

    @Test
    @DisplayName("multiple concatenated PDS subelements are all parsed")
    void multiplePds() throws Exception {
        // 0001/003/111  +  0043/005/ABCDE
        Map<String, String> data = adapter.extractPrivateData(
                msgWith(IsoField.ADDITIONAL_DATA_PRIVATE, "0001003111" + "0043005ABCDE"));
        assertThat(data)
                .containsEntry("mc.pds.0001", "111")
                .containsEntry("mc.pds.0043", "ABCDE");
    }

    @Test
    @DisplayName("a PDS length that overflows the string is dropped, not partially read")
    void pdsOverflowDropped() throws Exception {
        // tag 0043 claims length 999 but only "AB" remains
        Map<String, String> data = adapter.extractPrivateData(msgWith(IsoField.ADDITIONAL_DATA_PRIVATE, "0043999AB"));
        assertThat(data).isEmpty();
    }

    @Test
    @DisplayName("a non-numeric PDS length aborts parsing without throwing")
    void pdsInvalidLength() throws Exception {
        Map<String, String> data = adapter.extractPrivateData(msgWith(IsoField.ADDITIONAL_DATA_PRIVATE, "0043XYZAB"));
        assertThat(data).isEmpty();
    }

    @Test
    @DisplayName("MIP extended fields (DE111–125) are captured")
    void mipExtendedField() throws Exception {
        Map<String, String> data = adapter.extractPrivateData(msgWith(IsoField.MC_MIP_111, "MIPREF123"));
        assertThat(data).containsEntry("mc.mip.111", "MIPREF123");
    }

    @Test
    @DisplayName("a message with no Mastercard private data yields an empty map")
    void noPrivateData() throws Exception {
        assertThat(adapter.extractPrivateData(new ISOMsg())).isEmpty();
    }

    @Test
    @DisplayName("scheme type is MASTERCARD")
    void schemeType() {
        assertThat(adapter.getSchemeType()).isEqualTo(SchemeType.MASTERCARD);
    }
}

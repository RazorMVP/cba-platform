package com.cba.fep.scheme;

import com.cba.fep.iso.IsoField;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** QPBOC tags in DE55, read through the shared BER-TLV parser by {@link UnionPaySchemeAdapter#extractPrivateData}. */
class UnionPaySchemeAdapterTest {

    private final UnionPaySchemeAdapter adapter = new UnionPaySchemeAdapter(null);

    private static ISOMsg withIcc(String hex) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.set(IsoField.ICC_DATA, HexFormat.of().parseHex(hex.replace(" ", "")));
        return msg;
    }

    @Test
    @DisplayName("9F77 / 9F78 / 9F79 and a long-form 9F7C (81 length) are all read, inside a 77 template")
    void qpbocTagsWithLongFormLength() throws Exception {
        String customerExclusive = "C1".repeat(130); // 130 bytes → 81 82 length
        String inner = "9F77 06 000000100000"
                + " 9F78 06 000000005000"
                + " 9F79 06 000000075000"
                + " 9F7C 81 82 " + customerExclusive;
        int innerLen = HexFormat.of().parseHex(inner.replace(" ", "")).length; // 9+9+9+134 = 161
        String icc = "77 81 " + String.format("%02X", innerLen) + " " + inner + " 00 00"; // trailing padding

        Map<String, String> data = adapter.extractPrivateData(withIcc(icc));

        assertThat(data)
                .containsEntry("cup.vlp_funds_limit", "000000100000")
                .containsEntry("cup.vlp_txn_limit", "000000005000")
                .containsEntry("cup.vlp_available", "000000075000")
                .containsEntry("cup.customer_exclusive", customerExclusive);
    }

    @Test
    @DisplayName("malformed DE55 yields no QPBOC keys (and no exception)")
    void malformedIccIgnored() throws Exception {
        Map<String, String> data = adapter.extractPrivateData(withIcc("9F77 06 0000001000 9F79"));
        assertThat(data).doesNotContainKeys("cup.vlp_funds_limit", "cup.vlp_available");
    }
}

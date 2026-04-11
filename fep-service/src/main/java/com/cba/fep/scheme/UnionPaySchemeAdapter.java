package com.cba.fep.scheme;

import com.cba.fep.iso.IsoField;
import com.cba.fep.iso.IsoMessageFactory;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * China UnionPay (CUP) scheme adapter — CUPS/UICS with QPBOC contactless.
 *
 * <p>UnionPay private DEs (as defined in iso8583-unionpay.xml):
 * <ul>
 *   <li>DE 60: CUP Additional POS Information (terminal type, PIN capability, CUP category)</li>
 *   <li>DE 61: CUP Acquiring Institution Additional Data (UnionPay ICA, routing code)</li>
 *   <li>DE 62: CUP Additional Data (narration, wallet reference, dual-currency indicator)</li>
 *   <li>DE 63: CUP Security Control Information (MAC key index, CUPS session reference)</li>
 * </ul>
 *
 * <p>QPBOC (Quick Pass — UnionPay contactless) adds proprietary EMV tags inside DE55:
 * <ul>
 *   <li>Tag 9F7C: Customer Exclusive Data (32 bytes; CUP proprietary)</li>
 *   <li>Tag 9F77: VLP Funds Limit (6 bytes; Very Low Value Payment ceiling)</li>
 *   <li>Tag 9F78: VLP Single Transaction Limit (6 bytes)</li>
 *   <li>Tag 9F79: VLP Available Funds (6 bytes)</li>
 * </ul>
 *
 * <p>Dual-currency support: DE49 carries the transaction currency (e.g., USD for
 * foreign transactions at CUP-accepting merchants); DE50 carries settlement currency (CNY).
 * Conversion rate populated in DE9.
 *
 * <p>In production, UnionPay connectivity requires:
 * <ul>
 *   <li>CUP principal membership agreement via China UnionPay International (UPI)</li>
 *   <li>CUPS (China UnionPay Settlement) account registration</li>
 *   <li>UnionPay HSM key ceremony for QPBOC session MAC (SM4 algorithm for domestic; 3DES international)</li>
 * </ul>
 */
@Slf4j
@Component
public class UnionPaySchemeAdapter extends AbstractSchemeAdapter {

    // QPBOC EMV tag identifiers (as hex strings for TLV lookup)
    private static final String TAG_CUSTOMER_EXCLUSIVE = "9F7C";
    private static final String TAG_VLP_FUNDS_LIMIT    = "9F77";
    private static final String TAG_VLP_TXN_LIMIT      = "9F78";
    private static final String TAG_VLP_AVAILABLE      = "9F79";

    public UnionPaySchemeAdapter(IsoMessageFactory messageFactory) {
        super(messageFactory);
    }

    @Override
    public SchemeType getSchemeType() {
        return SchemeType.UNIONPAY;
    }

    @Override
    public Map<String, String> extractPrivateData(ISOMsg msg) throws ISOException {
        Map<String, String> data = new HashMap<>();

        // DE60: CUP Additional POS Information
        if (msg.hasField(IsoField.RESERVED_PRIVATE_60)) {
            String de60 = msg.getString(IsoField.RESERVED_PRIVATE_60);
            data.put("cup.pos_info", de60);
            if (de60 != null && de60.length() >= 5) {
                data.put("cup.terminal_type",    de60.substring(0, 2));
                data.put("cup.pin_capability",   de60.substring(2, 3));
                data.put("cup.category_code",    de60.substring(3, 5));
            }
        }

        // DE61: CUP Acquiring Additional Data
        if (msg.hasField(IsoField.RESERVED_PRIVATE_61)) {
            String de61 = msg.getString(IsoField.RESERVED_PRIVATE_61);
            data.put("cup.acquiring_data", de61);
            if (de61 != null && de61.length() >= 11) {
                data.put("cup.ica_code", de61.substring(0, 11).strip());
            }
        }

        // DE62: CUP Additional Data (narration, wallet, dual-currency flag)
        if (msg.hasField(IsoField.RESERVED_PRIVATE_62)) {
            data.put("cup.additional_data", msg.getString(IsoField.RESERVED_PRIVATE_62));
        }

        // DE63: CUP Security Control Information
        if (msg.hasField(IsoField.RESERVED_PRIVATE_63)) {
            String de63 = msg.getString(IsoField.RESERVED_PRIVATE_63);
            data.put("cup.security_info", de63);
            if (de63 != null && de63.length() >= 2) {
                data.put("cup.mac_key_index", de63.substring(0, 2));
            }
        }

        // DE55: parse QPBOC-specific tags from the EMV TLV stream
        if (msg.hasField(IsoField.ICC_DATA)) {
            byte[] iccData = msg.getBytes(IsoField.ICC_DATA);
            Map<String, byte[]> qpbocTags = parseQpbocTags(iccData);
            if (qpbocTags.containsKey(TAG_VLP_FUNDS_LIMIT)) {
                data.put("cup.vlp_funds_limit",
                        bytesToHex(qpbocTags.get(TAG_VLP_FUNDS_LIMIT)));
            }
            if (qpbocTags.containsKey(TAG_VLP_TXN_LIMIT)) {
                data.put("cup.vlp_txn_limit",
                        bytesToHex(qpbocTags.get(TAG_VLP_TXN_LIMIT)));
            }
            if (qpbocTags.containsKey(TAG_CUSTOMER_EXCLUSIVE)) {
                data.put("cup.customer_exclusive",
                        bytesToHex(qpbocTags.get(TAG_CUSTOMER_EXCLUSIVE)));
            }
        }

        // Dual-currency detection: transaction CCY != settlement CCY
        String txnCcy  = msg.getString(IsoField.CURRENCY_TRANSACTION);
        String settCcy = msg.getString(IsoField.CURRENCY_SETTLEMENT);
        if (txnCcy != null && settCcy != null && !txnCcy.equals(settCcy)) {
            data.put("cup.dual_currency", "true");
            data.put("cup.txn_currency",  txnCcy);
            data.put("cup.sett_currency", settCcy);
        }

        return data;
    }

    /**
     * Parse QPBOC-specific tags from an EMV TLV byte stream.
     * This is a simplified BER-TLV parser targeting the CUP proprietary tags.
     * Only 2-byte tags with 1-byte lengths are handled (covers 9F7C/9F77/9F78/9F79).
     */
    private Map<String, byte[]> parseQpbocTags(byte[] tlv) {
        Map<String, byte[]> tags = new HashMap<>();
        int pos = 0;
        while (pos < tlv.length - 2) {
            try {
                // Read tag — handle 1-byte and 2-byte tags
                int tag;
                int tagLen;
                if ((tlv[pos] & 0x1F) == 0x1F) {
                    // Two-byte tag
                    tag = ((tlv[pos] & 0xFF) << 8) | (tlv[pos + 1] & 0xFF);
                    tagLen = 2;
                } else {
                    tag = tlv[pos] & 0xFF;
                    tagLen = 1;
                }
                pos += tagLen;
                if (pos >= tlv.length) break;

                // Read length (simple form only — no BER extended length)
                int len = tlv[pos] & 0xFF;
                pos++;
                if (pos + len > tlv.length) break;

                byte[] value = new byte[len];
                System.arraycopy(tlv, pos, value, 0, len);
                tags.put(String.format("%X", tag), value);
                pos += len;
            } catch (ArrayIndexOutOfBoundsException e) {
                break;
            }
        }
        return tags;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}

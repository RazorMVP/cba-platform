package com.cba.fep.scheme;

import com.cba.fep.auth.AuthorizationResult;
import com.cba.fep.iso.IsoField;
import com.cba.fep.iso.IsoMessageFactory;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Mastercard scheme adapter — MIP (Mastercard Interchange Processing) extensions.
 *
 * <p>Mastercard private data — DE 48 PDS (Private Data Subelements):
 * <pre>
 *   Format: [TAG 4-digit][LEN 3-digit][VALUE] concatenated
 *   PDS 0001 = MDES token data
 *   PDS 0023 = Masterpass wallet data
 *   PDS 0037 = Additional merchant data (enhanced data capture)
 *   PDS 0043 = UCAF (Universal Cardholder Authentication Field — 3DS/SecureCode)
 * </pre>
 *
 * <p>Mastercard also uses DE 111–127 (MIP Extended Private Fields) for
 * scheme-specific routing, compliance, and tokenization metadata.
 *
 * <p>In production, Mastercard connectivity requires:
 * <ul>
 *   <li>MIP (Mastercard Interface Processor) certification</li>
 *   <li>ICA (Interchange Commission Acceptance) number registration</li>
 *   <li>MDES (Mastercard Digital Enablement Service) for tokenization</li>
 * </ul>
 */
@Slf4j
@Component
public class MastercardSchemeAdapter extends AbstractSchemeAdapter {

    private static final int PDS_TAG_LEN   = 4;
    private static final int PDS_LEN_LEN   = 3;

    public MastercardSchemeAdapter(IsoMessageFactory messageFactory) {
        super(messageFactory);
    }

    @Override
    public SchemeType getSchemeType() {
        return SchemeType.MASTERCARD;
    }

    @Override
    public Map<String, String> extractPrivateData(ISOMsg msg) throws ISOException {
        Map<String, String> data = new HashMap<>();

        // DE48: parse PDS subelements
        if (msg.hasField(IsoField.ADDITIONAL_DATA_PRIVATE)) {
            String pds = msg.getString(IsoField.ADDITIONAL_DATA_PRIVATE);
            if (pds != null) {
                parsePds(pds, data);
            }
        }

        // DE111–127: MIP extended fields
        for (int de = IsoField.MC_MIP_111; de <= IsoField.MC_MIP_125; de++) {
            if (msg.hasField(de)) {
                data.put("mc.mip." + de, msg.getString(de));
            }
        }
        return data;
    }

    @Override
    public void finalizeResponse(ISOMsg response, AuthorizationResult result, SchemeType scheme)
            throws ISOException {
        // Mastercard requires MIP routing confirmation in the response
        // In production, DE 111 would carry the MIP reference number
        if (result.mipReference() != null) {
            response.set(IsoField.MC_MIP_111, result.mipReference());
        }
    }

    /**
     * Parse Mastercard PDS (Private Data Subelements) from DE48.
     * Format: TAG(4) + LEN(3) + VALUE repeated, no delimiters.
     */
    private void parsePds(String pds, Map<String, String> out) {
        int pos = 0;
        while (pos + PDS_TAG_LEN + PDS_LEN_LEN < pds.length()) {
            String tag = pds.substring(pos, pos + PDS_TAG_LEN);
            pos += PDS_TAG_LEN;
            int len;
            try {
                len = Integer.parseInt(pds.substring(pos, pos + PDS_LEN_LEN));
            } catch (NumberFormatException e) {
                log.warn("PDS parse error at position {}: invalid length", pos);
                break;
            }
            pos += PDS_LEN_LEN;
            if (pos + len > pds.length()) {
                log.warn("PDS parse error: value overflows string at tag {}", tag);
                break;
            }
            String value = pds.substring(pos, pos + len);
            out.put("mc.pds." + tag, value);
            pos += len;
        }
    }
}

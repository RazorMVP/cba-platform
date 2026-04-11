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
 * Visa scheme adapter — BASE I / VisaNet / VIS extensions.
 *
 * <p>Visa-specific private DEs (as defined in iso8583-visa.xml):
 * <ul>
 *   <li>DE 60: Additional POS Information (terminal type, PIN capability, category codes)</li>
 *   <li>DE 61: POS Geographic Data (acquirer timezone, terminal geographic indicator)</li>
 *   <li>DE 62: Intermediate Network Facility (ICA, BIC, routing flags)</li>
 *   <li>DE 63: Network Data (STIP indicators, VbV/3DS CAVV, token data)</li>
 *   <li>DE 126: Visa Network Usage Data (settlement flags, reimbursement, ICA number)</li>
 * </ul>
 *
 * <p>In production, Visa BASE I connectivity requires:
 * <ul>
 *   <li>Visa Issuer Connection Agreement (ICA) registration</li>
 *   <li>VisaNet Connectivity Guide compliance (TLS 1.2+ + certificate pinning)</li>
 *   <li>Visa Risk Manager integration for STIP (Stand-In Processing) fallback</li>
 * </ul>
 */
@Slf4j
@Component
public class VisaSchemeAdapter extends AbstractSchemeAdapter {

    public VisaSchemeAdapter(IsoMessageFactory messageFactory) {
        super(messageFactory);
    }

    @Override
    public SchemeType getSchemeType() {
        return SchemeType.VISA;
    }

    @Override
    public Map<String, String> extractPrivateData(ISOMsg msg) throws ISOException {
        Map<String, String> data = new HashMap<>();

        // DE 60: Additional POS Information
        if (msg.hasField(IsoField.RESERVED_PRIVATE_60)) {
            data.put("visa.pos_info", msg.getString(IsoField.RESERVED_PRIVATE_60));
        }
        // DE 61: POS Geographic Data
        if (msg.hasField(IsoField.RESERVED_PRIVATE_61)) {
            data.put("visa.geo_data", msg.getString(IsoField.RESERVED_PRIVATE_61));
        }
        // DE 62: INF Data (ICA, BIC, routing)
        if (msg.hasField(IsoField.RESERVED_PRIVATE_62)) {
            String infData = msg.getString(IsoField.RESERVED_PRIVATE_62);
            data.put("visa.inf_data", infData);
            // ICA is first 11 chars of INF data in VisaNet format
            if (infData != null && infData.length() >= 11) {
                data.put("visa.ica", infData.substring(0, 11).strip());
            }
        }
        // DE 63: Network Data (STIP, VbV/3DS CAVV)
        if (msg.hasField(IsoField.RESERVED_PRIVATE_63)) {
            data.put("visa.network_data", msg.getString(IsoField.RESERVED_PRIVATE_63));
        }
        return data;
    }

    @Override
    public void finalizeResponse(ISOMsg response, AuthorizationResult result, SchemeType scheme)
            throws ISOException {
        // Populate DE63 with STIP decline indicator if authorization was stand-in
        if (result.standIn()) {
            // STIP indicator: "1" in byte 1 of DE63 network data
            response.set(IsoField.RESERVED_PRIVATE_63, "1");
            log.debug("VISA STIP: marked response DE63 with stand-in indicator");
        }
    }
}

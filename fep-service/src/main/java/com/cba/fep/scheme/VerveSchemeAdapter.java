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
 * Verve (Interswitch) scheme adapter — Nigeria domestic card scheme.
 *
 * <p>Verve private DEs (as defined in iso8583-verve.xml):
 * <ul>
 *   <li>DE 62: Verve/Interswitch Transaction Data
 *       — Interswitch routing code + wallet reference + narration + channel code</li>
 *   <li>DE 63: Verve/Interswitch Security Data
 *       — Session key reference + NIBSS routing identifier</li>
 * </ul>
 *
 * <p>Verve transactions settle through NIBSS (Nigeria Inter-Bank Settlement System).
 * The NIBSS routing identifier in DE63 directs the clearing message to the
 * correct NIBSS gateway endpoint.
 *
 * <p>In production, Verve connectivity requires:
 * <ul>
 *   <li>Interswitch principal membership agreement</li>
 *   <li>NIBSS settlement account registration</li>
 *   <li>Interswitch HSM key ceremony for session MAC keys</li>
 * </ul>
 */
@Slf4j
@Component
public class VerveSchemeAdapter extends AbstractSchemeAdapter {

    public VerveSchemeAdapter(IsoMessageFactory messageFactory) {
        super(messageFactory);
    }

    @Override
    public SchemeType getSchemeType() {
        return SchemeType.VERVE;
    }

    @Override
    public Map<String, String> extractPrivateData(ISOMsg msg) throws ISOException {
        Map<String, String> data = new HashMap<>();

        // DE62: Verve transaction data
        if (msg.hasField(IsoField.RESERVED_PRIVATE_62)) {
            String de62 = msg.getString(IsoField.RESERVED_PRIVATE_62);
            data.put("verve.txn_data", de62);
            if (de62 != null) {
                // Interswitch routing code: first 6 characters
                if (de62.length() >= 6) data.put("verve.routing_code", de62.substring(0, 6));
                // Wallet reference: next up to 20 characters (if present)
                if (de62.length() > 6) data.put("verve.wallet_ref", de62.substring(6).strip());
            }
        }

        // DE63: Verve security data
        if (msg.hasField(IsoField.RESERVED_PRIVATE_63)) {
            String de63 = msg.getString(IsoField.RESERVED_PRIVATE_63);
            data.put("verve.security_data", de63);
            if (de63 != null && de63.length() >= 6) {
                data.put("verve.nibss_routing_id", de63.substring(0, 6));
            }
        }
        return data;
    }
}

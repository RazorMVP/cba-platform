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
 * Afrigo (PAPSS) scheme adapter — Pan-African Payment and Settlement System.
 *
 * <p>Afrigo is the closest of the five schemes to base ISO 8583-1987.
 * It defines one proprietary extension:
 * <ul>
 *   <li>DE 60: PAPSS Routing Data
 *       — Format: source_country(3) + dest_country(3) + institution_code(11) + flags(variable)
 *       — Used by the PAPSS clearing infrastructure to route cross-border intra-African payments</li>
 * </ul>
 *
 * <p>Settlement occurs through the PAPSS clearing infrastructure using
 * African central bank nostro/vostro accounts. The DE60 institution code
 * identifies the originating/receiving central bank gateway.
 *
 * <p>In production, Afrigo connectivity requires:
 * <ul>
 *   <li>PAPSS principal member agreement (typically via central bank)</li>
 *   <li>PAPSS settlement account in the Afreximbank settlement facility</li>
 *   <li>ISO 3166-1 numeric country codes for source/dest in DE60</li>
 * </ul>
 */
@Slf4j
@Component
public class AfrigoSchemeAdapter extends AbstractSchemeAdapter {

    private static final int COUNTRY_CODE_LEN    = 3;
    private static final int INSTITUTION_CODE_LEN = 11;

    public AfrigoSchemeAdapter(IsoMessageFactory messageFactory) {
        super(messageFactory);
    }

    @Override
    public SchemeType getSchemeType() {
        return SchemeType.AFRIGO;
    }

    @Override
    public Map<String, String> extractPrivateData(ISOMsg msg) throws ISOException {
        Map<String, String> data = new HashMap<>();

        // DE60: PAPSS Routing Data
        if (msg.hasField(IsoField.RESERVED_PRIVATE_60)) {
            String de60 = msg.getString(IsoField.RESERVED_PRIVATE_60);
            data.put("papss.routing_data", de60);
            if (de60 != null) {
                int pos = 0;
                if (de60.length() >= pos + COUNTRY_CODE_LEN) {
                    data.put("papss.source_country", de60.substring(pos, pos + COUNTRY_CODE_LEN));
                    pos += COUNTRY_CODE_LEN;
                }
                if (de60.length() >= pos + COUNTRY_CODE_LEN) {
                    data.put("papss.dest_country", de60.substring(pos, pos + COUNTRY_CODE_LEN));
                    pos += COUNTRY_CODE_LEN;
                }
                if (de60.length() >= pos + INSTITUTION_CODE_LEN) {
                    data.put("papss.institution_code",
                            de60.substring(pos, pos + INSTITUTION_CODE_LEN).strip());
                    pos += INSTITUTION_CODE_LEN;
                }
                if (de60.length() > pos) {
                    data.put("papss.flags", de60.substring(pos));
                }
            }
        }
        return data;
    }
}

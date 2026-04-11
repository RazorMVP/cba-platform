package com.cba.fep.scheme;

import com.cba.fep.auth.AuthorizationResult;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

import java.util.Collections;
import java.util.Map;

/**
 * Fallback adapter for unregistered BINs.
 * Used when the PAN's BIN is not found in any registered scheme range.
 * The authorization handler will decline the transaction with RC=57.
 */
public class UnknownSchemeAdapter implements SchemeAdapter {

    @Override
    public SchemeType getSchemeType() {
        return SchemeType.UNKNOWN;
    }

    @Override
    public void applyPackager(ISOMsg msg) {
        // No packager change — use whatever was set by IsoMessageFactory.unpack()
    }

    @Override
    public Map<String, String> extractPrivateData(ISOMsg msg) {
        return Collections.emptyMap();
    }

    @Override
    public void embedArpc(ISOMsg response, byte[] arpc) {
        // No ARPC for unknown scheme
    }

    @Override
    public void finalizeResponse(ISOMsg response, AuthorizationResult result, SchemeType scheme)
            throws ISOException {
        response.set(39, "57"); // Transaction Not Permitted to Cardholder
    }
}

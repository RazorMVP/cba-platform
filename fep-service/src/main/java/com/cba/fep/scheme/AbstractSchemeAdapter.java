package com.cba.fep.scheme;

import com.cba.fep.auth.AuthorizationResult;
import com.cba.fep.emv.EmvTag;
import com.cba.fep.emv.tlv.BerTlvBuilder;
import com.cba.fep.iso.IsoField;
import com.cba.fep.iso.IsoMessageFactory;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Base implementation for scheme adapters.
 *
 * <p>Provides default implementations that are correct for most schemes.
 * Concrete adapters override only the methods where their scheme differs
 * from the base ISO 8583-1987 behavior.
 */
public abstract class AbstractSchemeAdapter implements SchemeAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final IsoMessageFactory messageFactory;

    protected AbstractSchemeAdapter(IsoMessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    @Override
    public void applyPackager(ISOMsg msg) throws ISOException {
        msg.setPackager(messageFactory.getPackager(getSchemeType()));
    }

    @Override
    public Map<String, String> extractPrivateData(ISOMsg msg) throws ISOException {
        // Default: no scheme-specific private data extraction
        return Collections.emptyMap();
    }

    @Override
    public void embedArpc(ISOMsg response, byte[] arpc, byte[] arc) throws ISOException {
        // DE55 of a 0110/0210 = tag 91 Issuer Authentication Data only (decision D8a): ARPC (8) || ARC (2).
        // Issuer scripts (71/72) would be appended here as further TLV objects; none are generated yet.
        if (arpc == null || arpc.length != 8 || arc == null || arc.length != 2) {
            throw new IllegalArgumentException("Tag 91 needs an 8-byte ARPC and a 2-byte ARC");
        }
        byte[] issuerAuthData = new byte[10];
        System.arraycopy(arpc, 0, issuerAuthData, 0, 8);
        System.arraycopy(arc, 0, issuerAuthData, 8, 2);
        response.set(IsoField.ICC_DATA, BerTlvBuilder.encode(EmvTag.ISSUER_AUTH_DATA, issuerAuthData));
    }

    @Override
    public void finalizeResponse(ISOMsg response, AuthorizationResult result, SchemeType scheme)
            throws ISOException {
        // Default: no scheme-specific finalization needed
    }
}

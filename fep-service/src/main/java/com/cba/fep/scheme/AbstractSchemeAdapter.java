package com.cba.fep.scheme;

import com.cba.fep.auth.AuthorizationResult;
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
    public void embedArpc(ISOMsg response, byte[] arpc) throws ISOException {
        // Build minimal DE55 update with ARPC tag (9F26) + ARC tag (8A)
        // In a production system this would merge with the existing DE55 TLV stream.
        // The EMV spec requires the ARPC to be in tag 9F26 position 2 of the ATC response.
        // For now, append the ARPC as raw bytes if DE55 was in the request.
        if (response.hasField(IsoField.ICC_DATA)) {
            byte[] existing = response.getBytes(IsoField.ICC_DATA);
            byte[] withArpc = appendArpcTag(existing, arpc);
            response.set(IsoField.ICC_DATA, withArpc);
        }
    }

    @Override
    public void finalizeResponse(ISOMsg response, AuthorizationResult result, SchemeType scheme)
            throws ISOException {
        // Default: no scheme-specific finalization needed
    }

    /**
     * Append the ARPC as a TLV-encoded tag 9F26 into the existing DE55 byte stream.
     * This is a simplified append — production code would use a proper TLV builder.
     */
    protected byte[] appendArpcTag(byte[] existingTlv, byte[] arpc) {
        // Tag 9F26 = ARQC/ARPC, length 8 bytes
        byte[] tag = new byte[]{(byte) 0x9F, 0x26};
        byte[] result = new byte[existingTlv.length + tag.length + 1 + arpc.length];
        System.arraycopy(existingTlv, 0, result, 0, existingTlv.length);
        System.arraycopy(tag, 0, result, existingTlv.length, tag.length);
        result[existingTlv.length + tag.length] = (byte) arpc.length;
        System.arraycopy(arpc, 0, result, existingTlv.length + tag.length + 1, arpc.length);
        return result;
    }
}

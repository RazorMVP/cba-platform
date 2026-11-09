package com.cba.fep.emv.tlv;

/** DE55 / ICC data that isn't valid EMV BER-TLV (truncated, over-long tag, unsupported length form). */
public class TlvParseException extends RuntimeException {

    public TlvParseException(String message) {
        super(message);
    }
}

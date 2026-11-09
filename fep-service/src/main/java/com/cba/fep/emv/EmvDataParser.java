package com.cba.fep.emv;

import com.cba.fep.emv.tlv.BerTlvParser;
import com.cba.fep.emv.tlv.Tlv;
import com.cba.fep.emv.tlv.TlvParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads EMV DE55 (ICC data) into a flat {@link EmvData} map of tag → value.
 *
 * <p>Decoding is {@link BerTlvParser} (EMV Book 3 Annex B: multi-byte tags, {@code 81}/{@code 82}
 * lengths, {@code 00}/{@code FF} padding). Templates (constructed tags such as {@code 70}, {@code 77})
 * are descended and only their primitive children are stored; the template tags themselves are not.
 *
 * <p>When a tag occurs more than once the <b>first</b> occurrence is kept (the rest are logged): a
 * duplicate {@code 9F26} appended after the genuine one must not replace the cryptogram that gets
 * validated.
 *
 * <p>Malformed ICC data gives an <b>empty</b> {@code EmvData} — no partial result. With no {@code 9F26}
 * the ARQC check fails, so a chip transaction with broken DE55 is not authenticated.
 */
@Slf4j
@Component
public class EmvDataParser {

    /**
     * Parse raw DE55 bytes into an {@link EmvData} object.
     *
     * @param iccData raw bytes from DE55
     * @return parsed EMV tags; empty if input is null, empty or malformed
     */
    public EmvData parse(byte[] iccData) {
        if (iccData == null || iccData.length == 0) {
            return new EmvData(Map.of());
        }
        List<Tlv> objects;
        try {
            objects = BerTlvParser.parse(iccData);
        } catch (TlvParseException e) {
            log.warn("EMV DE55 is not valid BER-TLV ({} bytes): {} — treated as no ICC data",
                    iccData.length, e.getMessage());
            return new EmvData(Map.of());
        }
        Map<String, byte[]> tags = new HashMap<>();
        flatten(objects, tags);
        log.debug("EMV parse: {} tags extracted from {} bytes", tags.size(), iccData.length);
        return new EmvData(tags);
    }

    private static void flatten(List<Tlv> objects, Map<String, byte[]> out) {
        for (Tlv t : objects) {
            if (t.constructed()) {
                flatten(t.children(), out);
            } else if (out.putIfAbsent(t.tag(), t.value()) != null) {
                log.warn("EMV DE55: duplicate tag {} ignored — first occurrence kept", t.tag());
            }
        }
    }
}

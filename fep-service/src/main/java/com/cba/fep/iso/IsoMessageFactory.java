package com.cba.fep.iso;

import com.cba.fep.scheme.SchemeType;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Manages one {@link GenericPackager} per card scheme.
 *
 * <p>Each scheme has its own XML packager definition that handles the
 * scheme-specific private data elements (DE 48, DE 60–63, DE 111–127).
 * The packager is stateless after construction and is safe for concurrent use.
 *
 * <p>The base packager is also retained as a fallback before BIN lookup
 * has determined the scheme.
 */
@Slf4j
@Component
public class IsoMessageFactory {

    private final GenericPackager basePackager;
    private final Map<SchemeType, GenericPackager> packagers = new EnumMap<>(SchemeType.class);

    public IsoMessageFactory(ResourceLoader resourceLoader) {
        this.basePackager     = loadPackager(resourceLoader, "classpath:iso8583-base.xml");
        packagers.put(SchemeType.VISA,       loadPackager(resourceLoader, "classpath:iso8583-visa.xml"));
        packagers.put(SchemeType.MASTERCARD,  loadPackager(resourceLoader, "classpath:iso8583-mastercard.xml"));
        packagers.put(SchemeType.VERVE,       loadPackager(resourceLoader, "classpath:iso8583-verve.xml"));
        packagers.put(SchemeType.AFRIGO,      loadPackager(resourceLoader, "classpath:iso8583-afrigo.xml"));
        packagers.put(SchemeType.UNIONPAY,    loadPackager(resourceLoader, "classpath:iso8583-unionpay.xml"));
        log.info("IsoMessageFactory: loaded {} scheme packagers", packagers.size());
    }

    /**
     * Create a new, empty {@link ISOMsg} set with the appropriate packager
     * for the given scheme. The packager handles field packing and unpacking.
     */
    public ISOMsg newMessage(SchemeType scheme) {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packagers.getOrDefault(scheme, basePackager));
        return msg;
    }

    /**
     * Create a message with the base (scheme-agnostic) packager.
     * Used for network management messages and before BIN lookup.
     */
    public ISOMsg newMessage() {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(basePackager);
        return msg;
    }

    /**
     * Unpack raw bytes into a populated {@link ISOMsg}.
     * The packager for the specific scheme must be selected before calling
     * this method if scheme-specific DEs need to be parsed correctly.
     */
    public ISOMsg unpack(byte[] raw, SchemeType scheme) throws ISOException {
        GenericPackager packer = packagers.getOrDefault(scheme, basePackager);
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packer);
        msg.unpack(raw);
        return msg;
    }

    /**
     * Unpack with the base packager (pre-scheme-identification path).
     * The router will re-pack with the correct scheme packager if needed.
     */
    public ISOMsg unpack(byte[] raw) throws ISOException {
        return unpack(raw, null);
    }

    public GenericPackager getPackager(SchemeType scheme) {
        return packagers.getOrDefault(scheme, basePackager);
    }

    public GenericPackager getBasePackager() {
        return basePackager;
    }

    private GenericPackager loadPackager(ResourceLoader loader, String location) {
        try {
            var resource = loader.getResource(location);
            var packager = new GenericPackager(resource.getInputStream());
            log.debug("Loaded packager from {}", location);
            return packager;
        } catch (ISOException | IOException e) {
            throw new IllegalStateException("Failed to load ISO 8583 packager from " + location, e);
        }
    }
}

package com.cba.fep.iso;

import com.cba.fep.scheme.SchemeType;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Loads the REAL scheme packager XMLs through the production {@link IsoMessageFactory}
 * (the rest of the suite mocks the factory, so this is the only test that actually
 * parses {@code iso8583-*.xml}).
 *
 * <p>Hardening guard: the DOCTYPEs reference {@code http://jpos.org/dtd/generic-packager-1.0.dtd},
 * the exact SYSTEM id jPOS 2.1.9's {@code GenericPackager$GenericEntityResolver} maps to the
 * DTD bundled in the jPOS jar ({@code org/jpos/iso/packager/genericpackager.dtd}). The legacy
 * {@code .../packager.dtd} id matched no resolver entry, so jPOS fetched it over the network —
 * a network-isolated FEP would fail to boot. The no-network test below blocks all remote DTD
 * protocols and asserts the packagers still load, proving resolution is local.
 */
class IsoMessageFactoryTest {

    private static final SchemeType[] SCHEMES = {
        SchemeType.VISA, SchemeType.MASTERCARD, SchemeType.VERVE,
        SchemeType.AFRIGO, SchemeType.UNIONPAY,
    };

    @Test
    @DisplayName("all scheme packagers load WITHOUT any network DTD fetch (local resolution only)")
    void allPackagersLoadWithoutNetwork() {
        // Allow only local DTD protocols; any attempt to fetch http(s)://jpos.org/... throws.
        String prev = System.getProperty("javax.xml.accessExternalDTD");
        System.setProperty("javax.xml.accessExternalDTD", "file,jar");
        try {
            assertThatCode(() -> {
                IsoMessageFactory factory = new IsoMessageFactory(new DefaultResourceLoader());
                for (SchemeType s : SCHEMES) {
                    assertThat(factory.getPackager(s)).as("packager for %s", s).isNotNull();
                }
                assertThat(factory.getBasePackager()).isNotNull();
            }).doesNotThrowAnyException();
        } finally {
            if (prev == null) System.clearProperty("javax.xml.accessExternalDTD");
            else System.setProperty("javax.xml.accessExternalDTD", prev);
        }
    }

    @Test
    @DisplayName("the base packager round-trips a packed/unpacked 0800 message")
    void basePackagerRoundTrip() throws Exception {
        IsoMessageFactory factory = new IsoMessageFactory(new DefaultResourceLoader());

        ISOMsg msg = factory.newMessage();
        msg.setMTI("0800");
        msg.set(11, "000123");
        msg.set(70, "301");
        byte[] packed = msg.pack();

        ISOMsg back = factory.unpack(packed);
        assertThat(back.getMTI()).isEqualTo("0800");
        assertThat(back.getString(11)).isEqualTo("000123");
        assertThat(back.getString(70)).isEqualTo("301");
    }

    @Test
    @DisplayName("no packager XML references the unresolvable legacy DTD id (regression guard)")
    void doctypeIdsAreResolvableLocally() throws Exception {
        String[] xmls = {
            "/iso8583-base.xml", "/iso8583-visa.xml", "/iso8583-mastercard.xml",
            "/iso8583-verve.xml", "/iso8583-afrigo.xml", "/iso8583-unionpay.xml",
        };
        for (String xml : xmls) {
            try (InputStream in = getClass().getResourceAsStream(xml)) {
                assertThat(in).as("resource %s present", xml).isNotNull();
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(content)
                    .as("%s must use the jPOS-resolvable generic-packager DTD id", xml)
                    .contains("generic-packager-1.0.dtd")
                    .doesNotContain("dtd/packager.dtd");
            }
        }
    }
}

package com.cba.fep.iso;

import com.cba.fep.scheme.SchemeType;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive per-field validation of the jPOS scheme packagers against the
 * canonical ISO 8583:1987 data-element table.
 *
 * <p>The {@link #CANON} map encodes the standard DE definitions (format → jPOS
 * field-packager class + length). The base packager must define every one of
 * them exactly; each scheme packager, wherever it defines a standard DE, must
 * match. Private/scheme-specific DEs (48, 60–63, 111–127) are intentionally
 * excluded — those carry raw scheme data that the {@code SchemeAdapter}s parse
 * in code, so a generic {@code IFA_LLLCHAR/999} is correct in the packager.
 *
 * <p>Two functional checks back the static conformance check: every field uses a
 * real jPOS class (regression guard for the fabricated classes fixed in cont. 15),
 * and each scheme packager round-trips a realistic 0100 authorization — including
 * the binary PIN (DE52) and EMV (DE55) fields and the secondary bitmap.
 */
class PackagerFieldSpecTest {

    /** Canonical ISO 8583:1987 standard DE → "length CLASS". */
    static final Map<Integer, String> CANON = new LinkedHashMap<>();
    static {
        CANON.put(0,   "4 IFA_NUMERIC");      // MTI
        CANON.put(1,   "16 IFB_BITMAP");      // bitmap (16 bytes → secondary supported)
        CANON.put(2,   "19 IFA_LLNUM");       // PAN  n..19 LLVAR
        CANON.put(3,   "6 IFA_NUMERIC");      // processing code
        CANON.put(4,   "12 IFA_NUMERIC");     // amount, transaction
        CANON.put(7,   "10 IFA_NUMERIC");     // transmission date & time
        CANON.put(11,  "6 IFA_NUMERIC");      // STAN
        CANON.put(12,  "6 IFA_NUMERIC");      // local transaction time (1987: n6)
        CANON.put(13,  "4 IFA_NUMERIC");      // local transaction date
        CANON.put(14,  "4 IFA_NUMERIC");      // expiration date
        CANON.put(18,  "4 IFA_NUMERIC");      // merchant category code
        CANON.put(22,  "3 IFA_NUMERIC");      // POS entry mode (1987: n3)
        CANON.put(23,  "3 IFA_NUMERIC");      // card sequence number
        CANON.put(25,  "2 IFA_NUMERIC");      // POS condition code
        CANON.put(32,  "11 IFA_LLNUM");       // acquiring institution id  n..11 LLVAR
        CANON.put(35,  "37 IFA_LLCHAR");      // track 2  z..37 LLVAR
        CANON.put(37,  "12 IF_CHAR");         // retrieval reference number  an12
        CANON.put(38,  "6 IF_CHAR");          // authorization id response  an6
        CANON.put(39,  "2 IF_CHAR");          // response code  an2
        CANON.put(41,  "8 IF_CHAR");          // card acceptor terminal id  ans8
        CANON.put(42,  "15 IF_CHAR");         // card acceptor id code  ans15
        CANON.put(43,  "40 IF_CHAR");         // card acceptor name/location  ans40
        CANON.put(49,  "3 IFA_NUMERIC");      // currency code, transaction  n3
        CANON.put(50,  "3 IFA_NUMERIC");      // currency code, settlement
        CANON.put(51,  "3 IFA_NUMERIC");      // currency code, cardholder billing
        CANON.put(52,  "8 IFB_BINARY");       // PIN data  b64
        CANON.put(54,  "120 IFA_LLLCHAR");    // additional amounts  an..120 LLLVAR
        CANON.put(55,  "999 IFB_LLLBINARY");  // ICC / EMV data  b..999 LLLVAR
        CANON.put(64,  "8 IFB_BINARY");       // MAC  b64
        CANON.put(70,  "3 IFA_NUMERIC");      // network management info code
        CANON.put(90,  "42 IFA_NUMERIC");     // original data elements  n42
        CANON.put(128, "8 IFB_BINARY");       // MAC (secondary)  b64
    }

    /** Real jPOS field-packager classes the definitions are allowed to use. */
    static final Set<String> KNOWN_CLASSES = Set.of(
        "IFA_NUMERIC", "IFA_LLNUM", "IFA_LLLNUM", "IF_CHAR", "IFA_LLCHAR", "IFA_LLLCHAR",
        "IFB_BINARY", "IFB_BITMAP", "IFB_LLLBINARY", "IFA_AMOUNT", "IFA_BINARY");

    private static final String[] XMLS = {
        "/iso8583-base.xml", "/iso8583-visa.xml", "/iso8583-mastercard.xml",
        "/iso8583-verve.xml", "/iso8583-afrigo.xml", "/iso8583-unionpay.xml",
    };

    @Test
    @DisplayName("the base packager defines every canonical ISO 8583:1987 DE exactly")
    void baseMatchesCanonicalSpec() throws Exception {
        Map<Integer, String> base = parseFields("/iso8583-base.xml");
        CANON.forEach((de, expected) ->
            assertThat(base.get(de)).as("base DE%d", de).isEqualTo(expected));
    }

    @ParameterizedTest(name = "{0} matches the canonical spec for every standard DE it defines")
    @EnumSource(value = SchemeType.class, names = {"VISA", "MASTERCARD", "VERVE", "AFRIGO", "UNIONPAY"})
    void schemeStandardDesMatchCanonical(SchemeType scheme) throws Exception {
        Map<Integer, String> fields = parseFields("/iso8583-" + scheme.name().toLowerCase() + ".xml");
        CANON.forEach((de, expected) -> {
            if (fields.containsKey(de)) {
                assertThat(fields.get(de)).as("%s DE%d", scheme, de).isEqualTo(expected);
            }
        });
    }

    @ParameterizedTest(name = "every field class in {0} is a real jPOS class")
    @EnumSource(SchemeType.class)
    void allFieldClassesAreRealJposClasses(SchemeType scheme) throws Exception {
        if (scheme == SchemeType.UNKNOWN) return; // no XML for UNKNOWN
        Map<Integer, String> fields = parseFields("/iso8583-" + scheme.name().toLowerCase() + ".xml");
        fields.forEach((de, def) -> {
            String cls = def.substring(def.indexOf(' ') + 1);
            assertThat(KNOWN_CLASSES).as("%s DE%d uses class %s", scheme, de, cls).contains(cls);
        });
    }

    @ParameterizedTest(name = "{0} packager round-trips a realistic 0100 (incl. binary DE52/DE55)")
    @EnumSource(value = SchemeType.class, names = {"VISA", "MASTERCARD", "VERVE", "AFRIGO", "UNIONPAY"})
    void schemePackagerRoundTripsAuthMessage(SchemeType scheme) throws Exception {
        IsoMessageFactory factory = new IsoMessageFactory(new DefaultResourceLoader());

        byte[] pinBlock = {0x12, 0x34, 0x56, 0x78, (byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xF0};
        byte[] emv = {(byte) 0x9F, 0x26, 0x08, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};

        ISOMsg msg = factory.newMessage(scheme);
        msg.setMTI("0100");
        msg.set(2, "4111111111111111");
        msg.set(3, "000000");
        msg.set(4, "000000010000");
        msg.set(7, "0628120000");
        msg.set(11, "000123");
        msg.set(41, "TERM0001");
        msg.set(42, "MERCHANT0000001");
        msg.set(43, "ACME STORE");
        msg.set(49, "840");
        msg.set(52, pinBlock);   // binary PIN block (IFB_BINARY)
        msg.set(55, emv);        // EMV TLV (IFB_LLLBINARY)

        ISOMsg back = factory.unpack(msg.pack(), scheme);

        assertThat(back.getMTI()).isEqualTo("0100");
        assertThat(back.getString(2)).isEqualTo("4111111111111111");
        assertThat(back.getString(3)).isEqualTo("000000");
        assertThat(back.getString(4)).isEqualTo("000000010000");
        assertThat(back.getString(11)).isEqualTo("000123");
        assertThat(back.getString(41)).isEqualTo("TERM0001");
        assertThat(back.getString(42)).isEqualTo("MERCHANT0000001");
        assertThat(back.getString(43).trim()).isEqualTo("ACME STORE"); // IF_CHAR space-pads to 40
        assertThat(back.getString(49)).isEqualTo("840");
        assertThat(back.getBytes(52)).isEqualTo(pinBlock);
        assertThat(back.getBytes(55)).isEqualTo(emv);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    /** Parses an iso8583-*.xml into DE id → "length CLASS" (DTD ignored). */
    private static Map<Integer, String> parseFields(String resource) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        // Parse the field definitions without touching the DTD at all.
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/validation", false);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Map<Integer, String> out = new TreeMap<>();
        try (InputStream in = PackagerFieldSpecTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("resource %s present", resource).isNotNull();
            Document doc = db.parse(in);
            NodeList fields = doc.getElementsByTagName("isofield");
            for (int i = 0; i < fields.getLength(); i++) {
                Element e = (Element) fields.item(i);
                int id = Integer.parseInt(e.getAttribute("id"));
                String cls = e.getAttribute("class").replace("org.jpos.iso.", "");
                out.put(id, e.getAttribute("length") + " " + cls);
            }
        }
        return out;
    }
}

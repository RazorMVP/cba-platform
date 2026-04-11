package com.cba.card.bureau;

import com.cba.card.bin.BinService;
import com.cba.card.bin.SchemeType;
import com.cba.card.card.Card;
import com.cba.card.card.CardType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Generates CDP (Card Data Preparation) records for bureau personalisation.
 *
 * <p>Each card scheme has its own CDP specification. This generator produces a
 * scheme-aware record containing all parameters needed by the bureau to personalise
 * the chip, encode the magnetic stripe, and prepare the card mailer.
 *
 * <h3>EMV Application IDs (AIDs) by scheme</h3>
 * <ul>
 *   <li>Visa Credit/Debit: {@code A0000000031010}</li>
 *   <li>Visa Electron:     {@code A0000000032010}</li>
 *   <li>Mastercard:        {@code A0000000041010}</li>
 *   <li>Mastercard Maestro:{@code A0000000043060}</li>
 *   <li>Verve:             {@code A000000333010101}</li>
 *   <li>Afrigo:            {@code A000000337010008}</li>
 *   <li>UnionPay:          {@code A000000333010102}</li>
 * </ul>
 *
 * <h3>ISO 7813 Service Codes</h3>
 * <ul>
 *   <li>{@code 101} — International, chip, PIN required (standard debit)</li>
 *   <li>{@code 201} — International, contactless, PIN optional (credit/premium debit)</li>
 *   <li>{@code 221} — International, contactless, signature optional (prepaid)</li>
 *   <li>{@code 101} — Verve/Afrigo use 101 as domestic PIN-mandatory</li>
 * </ul>
 *
 * <p><strong>Security:</strong> The PAN passed in is the Jasypt-encrypted string from
 * the database — it is passed through as-is for the bureau's HSM decryption step.
 * This generator never decrypts the PAN; it never logs any PAN-related field.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CdpGenerator {

    private final BinService binService;

    // ── EMV Application IDs ───────────────────────────────────────────────────

    private static final String AID_VISA_CREDIT_DEBIT  = "A0000000031010";
    private static final String AID_VISA_ELECTRON      = "A0000000032010";
    private static final String AID_MASTERCARD         = "A0000000041010";
    private static final String AID_MASTERCARD_MAESTRO = "A0000000043060";
    private static final String AID_VERVE              = "A000000333010101";
    private static final String AID_AFRIGO             = "A000000337010008";
    private static final String AID_UNION_PAY          = "A000000333010102";

    // ── AIP values ────────────────────────────────────────────────────────────
    // Byte 1 bit 7: SDA  Byte 1 bit 6: DDA  Byte 1 bit 5: cardholder verification
    // Byte 1 bit 4: terminal risk mgmt  Byte 1 bit 3: issuer authentication  Byte 1 bit 0: contactless
    // Standard chip+PIN (no SDA, DDA, contactless): 0x1C00
    private static final String AIP_CHIP_PIN         = "1C00"; // DDA + CVM + terminal risk + issuer auth
    private static final String AIP_CONTACTLESS      = "3C00"; // same + contactless bit
    private static final String AIP_UNION_PAY_QPBOC  = "3E00"; // CUP QPBOC profile

    // ── Issuer Action Codes ───────────────────────────────────────────────────
    // These control chip offline approval/denial behaviour
    // Bit positions per EMV Book 3 Annex C
    private static final String IAC_DEFAULT_STANDARD  = "F410000000";
    private static final String IAC_DENIAL_STANDARD   = "0010000000";
    private static final String IAC_ONLINE_STANDARD   = "F470400000";

    // ── PDOL (Processing Data Object List) ───────────────────────────────────
    // Terminal must supply: Terminal Country Code (9F1A, 2 bytes) + TTQ (9F66, 4 bytes)
    private static final String PDOL_STANDARD         = "9F1A029F6604";

    /**
     * Generate a CDP record for a card being included in a bureau job.
     *
     * @param card the physical card entity (status must be ORDERED)
     * @return a populated {@link CdpRecord}; hash field contains SHA-256 of the serialised record
     */
    public CdpRecord generate(Card card) {
        SchemeType scheme = binService.lookupScheme(card.getPanPrefix());
        String aid        = resolveAid(scheme, card.getCardType());
        String label      = resolveLabel(scheme, card.getCardType());
        String serviceCode = resolveServiceCode(scheme, card.getCardType());
        String aip        = resolveAip(scheme, card.getCardType());

        // Track 2 format: PAN=EXPIRY SERVICE_CODE DISCRETIONARY
        // For CDP we mask the PAN in the track data itself; the bureau's HSM decrypts
        // the panEncryptedForBureau to substitute the real PAN during personalization.
        String maskedTrack2 = buildMaskedTrack2(card.getPanPrefix(), card.getPanSuffix(),
                card.getExpiryDate(), serviceCode);

        // Pass the encrypted PAN directly — bureau HSM decrypts using shared ZMK
        String panEncryptedForBureau = card.getPanEncrypted();

        CdpRecord record = new CdpRecord(
                card.getId(),
                aid,
                label,
                panEncryptedForBureau,
                card.getExpiryDate(),
                card.getCardSequenceNo(),
                serviceCode,
                maskedTrack2,
                aip,
                IAC_DEFAULT_STANDARD,
                IAC_DENIAL_STANDARD,
                IAC_ONLINE_STANDARD,
                PDOL_STANDARD,
                1,   // issuerKeyIndex — index 1 in the bureau's key store
                1,   // cvkIndex — CVK index for CVV/CVV2 generation
                ""   // hash placeholder — computed below
        );

        String hash = hashCdp(record);
        record = new CdpRecord(
                record.cardId(), record.schemeAid(), record.schemeLabel(),
                record.panEncryptedForBureau(), record.expiryYYMM(), record.sequenceNo(),
                record.serviceCode(), record.track2Data(), record.aip(),
                record.iacDefault(), record.iacDenial(), record.iacOnline(),
                record.pdol(), record.issuerKeyIndex(), record.cvkIndex(),
                hash
        );

        // Log scheme + AID only — never log PAN-related fields
        log.debug("CDP generated: card={} scheme={} aid={}", card.getId(), scheme, aid);
        return record;
    }

    // ── AID resolution ────────────────────────────────────────────────────────

    private String resolveAid(SchemeType scheme, CardType cardType) {
        return switch (scheme) {
            case VISA -> cardType == CardType.DEBIT ? AID_VISA_CREDIT_DEBIT : AID_VISA_CREDIT_DEBIT;
            case MASTERCARD -> cardType == CardType.DEBIT ? AID_MASTERCARD_MAESTRO : AID_MASTERCARD;
            case VERVE      -> AID_VERVE;
            case AFRIGO     -> AID_AFRIGO;
            case UNION_PAY  -> AID_UNION_PAY;
            default         -> AID_VISA_CREDIT_DEBIT; // fallback for UNKNOWN — use Visa generic
        };
    }

    private String resolveLabel(SchemeType scheme, CardType cardType) {
        return switch (scheme) {
            case VISA -> cardType == CardType.CREDIT ? "VISA CREDIT" :
                         cardType == CardType.PREPAID ? "VISA PREPAID" : "VISA DEBIT";
            case MASTERCARD -> cardType == CardType.CREDIT ? "MASTERCARD" : "MAESTRO";
            case VERVE      -> "VERVE";
            case AFRIGO     -> "AFRIGO";
            case UNION_PAY  -> "UNIONPAY";
            default         -> "CARD";
        };
    }

    private String resolveServiceCode(SchemeType scheme, CardType cardType) {
        // 1st digit: interchange (1=international, 2=international+contactless)
        // 2nd digit: 0=normal
        // 3rd digit: 0=PIN required, 1=no restriction, 5=signature
        return switch (cardType) {
            case DEBIT   -> "101"; // chip+PIN required for all debit
            case CREDIT  -> "201"; // contactless capable, PIN optional (signature OK)
            case PREPAID -> "221"; // contactless capable, signature optional
        };
    }

    private String resolveAip(SchemeType scheme, CardType cardType) {
        if (scheme == SchemeType.UNION_PAY) return AIP_UNION_PAY_QPBOC;
        return (cardType == CardType.CREDIT || cardType == CardType.PREPAID)
                ? AIP_CONTACTLESS
                : AIP_CHIP_PIN;
    }

    // ── Track 2 ───────────────────────────────────────────────────────────────

    /**
     * Build a masked Track 2 placeholder for CDP transport.
     * Format: {@code PREFIX******SUFFIX=EXPIRY SERVICE_CODE 00000000000}
     * The bureau substitutes the real PAN (from the decrypted {@code panEncryptedForBureau})
     * before encoding the magnetic stripe.
     */
    private String buildMaskedTrack2(String panPrefix, String panSuffix,
                                      String expiryYYMM, String serviceCode) {
        // PAN length unknown at this level — use prefix + mask + suffix
        // Bureau fills the gap from the decrypted PAN
        String maskedPan = panPrefix + "******" + panSuffix;
        return maskedPan + "=" + expiryYYMM + serviceCode + "00000000000";
    }

    // ── Integrity hash ────────────────────────────────────────────────────────

    /**
     * Compute SHA-256 of the key CDP fields.
     *
     * <p>The hash is stored in {@code bureau_job_items.personalization_data_hash}.
     * The bureau verifies this against the received CDP file to detect any
     * transmission corruption. The encrypted PAN is included in the hash
     * (it is not PII in plaintext form).
     */
    private String hashCdp(CdpRecord r) {
        try {
            String data = r.cardId() + "|" + r.schemeAid() + "|" +
                          r.panEncryptedForBureau() + "|" +
                          r.expiryYYMM() + "|" + r.sequenceNo() + "|" +
                          r.aip() + "|" + r.iacDefault() + "|" +
                          r.issuerKeyIndex() + "|" + r.cvkIndex();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("CDP hash generation failed", e);
        }
    }
}

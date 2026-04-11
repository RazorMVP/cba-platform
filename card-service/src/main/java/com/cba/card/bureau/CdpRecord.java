package com.cba.card.bureau;

/**
 * Card Data Preparation (CDP) record — the per-card payload transmitted to the
 * card personalization bureau for chip and magnetic stripe personalisation.
 *
 * <p>In production, this record is serialised to the scheme-specific binary or
 * structured-text CDP format (Visa VIS CDP, MC M/Chip CDP, Verve CDP) and
 * bundled into an encrypted SFTP transmission to the bureau.
 *
 * <p><strong>Security note:</strong> The {@code panEncryptedForBureau} field carries
 * the PAN encrypted under a Zone Master Key (ZMK) shared between the issuer's HSM
 * and the bureau's HSM. The plaintext PAN never appears in logs, databases, or
 * REST responses. The {@code hash} field stores the SHA-256 of the full CDP bytes
 * and is the only value persisted in {@code bureau_job_items}.
 *
 * @param cardId               UUID of the card entity (traceability only — never in wire format)
 * @param schemeAid            EMV Application Identifier (e.g. A0000000031010 for Visa Credit)
 * @param schemeLabel          Human-readable application label for the EMV menu (e.g. "VISA CREDIT")
 * @param panEncryptedForBureau PAN encrypted under the issuer-bureau ZMK — bureau decrypts for chip load
 * @param expiryYYMM           Card expiry in YYMM format (DE14)
 * @param sequenceNo           Card sequence number (01–99; usually 01 for first card on PAN)
 * @param serviceCode          ISO 7813 service code (e.g. "101"=chip+PIN, "201"=contactless)
 * @param track2Data           ISO 7813 Track 2 data with PAN masked for transport
 * @param aip                  Application Interchange Profile hex — 2 bytes indicating chip capabilities
 * @param iacDefault           Issuer Action Code – Default (5 bytes hex)
 * @param iacDenial            Issuer Action Code – Denial (5 bytes hex)
 * @param iacOnline            Issuer Action Code – Online (5 bytes hex)
 * @param pdol                 Processing Data Object List — tags requested from terminal at authorisation
 * @param issuerKeyIndex       Issuer Application Key index loaded onto chip (1-based; bureau resolves actual key)
 * @param cvkIndex             Card Verification Key index for CVV/CVV2 generation
 * @param hash                 SHA-256 hex of the full serialised CDP record — for integrity verification
 */
public record CdpRecord(
        java.util.UUID  cardId,
        String          schemeAid,
        String          schemeLabel,
        String          panEncryptedForBureau,
        String          expiryYYMM,
        int             sequenceNo,
        String          serviceCode,
        String          track2Data,
        String          aip,
        String          iacDefault,
        String          iacDenial,
        String          iacOnline,
        String          pdol,
        int             issuerKeyIndex,
        int             cvkIndex,
        String          hash
) {}

package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mastercard IPM (Interchange Posting and Messaging) clearinghouse file exporter.
 *
 * <h3>Stub status</h3>
 * Replace {@link #export} with the real IPM serializer once the Mastercard
 * GCMS/IPM specification is available from your Mastercard relationship manager.
 *
 * <h3>IPM format overview</h3>
 * <ul>
 *   <li>IPM files are ISO 8583-1987 based: each record is an ISO 8583 message</li>
 *   <li>Message types: 1644 (header/trailer control records), 1240 (financial presentment)</li>
 *   <li>DE48 PDS (Private Data Subelements) carries Mastercard-specific clearing fields</li>
 *   <li>DE111-DE127 carry additional GCMS-specific data elements</li>
 *   <li>File framing: 2-byte big-endian record length prefix (same as ISO 8583 TCP framing)</li>
 *   <li>Transmission: SFTP to Banknet ({@code banknet.mastercard.com})</li>
 * </ul>
 *
 * <h3>Key IPM record structure (from public Mastercard documentation)</h3>
 * <pre>
 *   MTI 1644 — File Header/Trailer Control Record
 *     DE1  = Secondary Bitmap
 *     DE70 = Network Management Info Code (001=sign-on, 002=sign-off)
 *   MTI 1240 — Financial Presentment
 *     DE2  = PAN
 *     DE3  = Processing Code
 *     DE4  = Transaction Amount
 *     DE12 = Local Transaction Time
 *     DE13 = Local Transaction Date
 *     DE22 = POS Entry Mode
 *     DE38 = Authorization Code
 *     DE42 = Merchant ID
 *     DE48 = PDS (Private Data Subelements — clearing-specific)
 *     ...additional DEs per GCMS spec
 * </pre>
 *
 * <h3>To complete this implementation</h3>
 * <ol>
 *   <li>Obtain Mastercard GCMS/IPM specification from your Mastercard relationship manager</li>
 *   <li>Implement ISO 8583 record serializer using jPOS ISOMsg (already on classpath in fep-service)</li>
 *   <li>Set {@code card.settlement.export.schemes.mastercard.enabled=true}</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MastercardIpmExporter implements SettlementFileExporter {

    private final SettlementExportProperties props;

    @Override
    public String getScheme() { return "MASTERCARD"; }

    @Override
    public boolean isEnabled() {
        return props.forScheme("mastercard").isEnabled();
    }

    @Override
    public String transmissionMethod() { return "SFTP"; }

    /**
     * Generates the Mastercard IPM filename.
     * Format: member-id + YYMMDD + sequence — example: CBA00120261130001.IPM
     */
    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        String participantId = resolveParticipantId();
        String date = exportDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        return participantId + date + "001.IPM";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("[STUB] MastercardIpmExporter.export() called: {} records for batch={} date={}",
                records.size(), batch.getBatchRef(), exportDate);

        StringBuilder sb = new StringBuilder();
        sb.append("=== MASTERCARD IPM STUB FILE — NOT FOR PRODUCTION ===\n");
        sb.append("Batch: ").append(batch.getBatchRef()).append("\n");
        sb.append("Settlement Date: ").append(exportDate).append("\n");
        sb.append("Record Count: ").append(records.size()).append("\n\n");
        sb.append("FORMAT: ISO 8583-1987 framed records (2-byte length prefix per record)\n\n");
        sb.append("RECORD TYPES (IPM):\n");
        sb.append("  MTI 1644 — File Header (DE70=001) and Trailer (DE70=002)\n");
        sb.append("  MTI 1240 — Financial Presentment (one per cleared transaction)\n\n");
        sb.append("KEY DATA ELEMENTS PER 1240 RECORD:\n");
        sb.append("  DE2  PAN\n");
        sb.append("  DE3  Processing Code\n");
        sb.append("  DE4  Transaction Amount\n");
        sb.append("  DE22 POS Entry Mode\n");
        sb.append("  DE38 Authorization Code\n");
        sb.append("  DE42 Merchant ID\n");
        sb.append("  DE48 PDS (Private Data Subelements per GCMS spec)\n");
        sb.append("  DE49 Currency Code\n\n");
        sb.append("DATA RECORDS:\n");
        for (SettlementExportRecord r : records) {
            sb.append(String.format("  pan=%s stan=%s rrn=%s auth=%s amount=%s ccy=%s mcc=%s%n",
                    r.maskedPan(), r.stan(), r.rrn(), r.authCode(),
                    r.grossAmount(), r.currencyCode(), r.mcc()));
        }
        sb.append("\n=== END OF STUB FILE ===\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String resolveParticipantId() {
        String pid = props.forScheme("mastercard").getParticipantId();
        return (pid != null && !pid.isBlank()) ? pid : props.getMemberId();
    }
}

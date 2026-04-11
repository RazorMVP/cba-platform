package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Verve / NIBSS (Nigeria Inter-Bank Settlement System) e-settlement file exporter.
 *
 * <h3>Stub status</h3>
 * Replace {@link #export} with the real NIBSS e-settlement serializer once the
 * NIBSS e-settlement specification is available from Interswitch / NIBSS.
 *
 * <h3>NIBSS e-settlement format overview</h3>
 * <ul>
 *   <li>Primarily used for domestic Nigerian card transactions on the Verve scheme</li>
 *   <li>Interswitch acts as the central clearing switch; NIBSS handles final settlement</li>
 *   <li>File format: structured CSV or delimited flat file (format version varies)</li>
 *   <li>Currency: Nigerian Naira (NGN, ISO 4217 numeric 566) in kobo (minor units)</li>
 *   <li>Transmission: SFTP to NIBSS ({@code nibss-plc.com.ng}) or Interswitch gateway</li>
 *   <li>Regulatory context: CBN (Central Bank of Nigeria) mandates for real-time settlement</li>
 * </ul>
 *
 * <h3>Key fields (NIBSS e-settlement outline)</h3>
 * <pre>
 *   Field            Description
 *   InstitutionCode  NIBSS member bank code
 *   SessionID        Unique transaction session ID
 *   TerminalID       Acquiring terminal ID
 *   RRN              Retrieval Reference Number
 *   PAN              Masked PAN (last 4 digits only in file)
 *   Amount           Transaction amount in kobo
 *   ResponseCode     ISO 8583 DE39
 *   MerchantID       Acquiring merchant ID
 *   TransactionDate  YYYYMMDDHHMMSS
 * </pre>
 *
 * <h3>To complete this implementation</h3>
 * <ol>
 *   <li>Obtain NIBSS e-settlement spec from Interswitch technical team</li>
 *   <li>Implement CSV/flat-file serializer per the field table above</li>
 *   <li>Set {@code card.settlement.export.schemes.verve.enabled=true}</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerveNibssExporter implements SettlementFileExporter {

    private final SettlementExportProperties props;

    @Override
    public String getScheme() { return "VERVE"; }

    @Override
    public boolean isEnabled() {
        return props.forScheme("verve").isEnabled();
    }

    @Override
    public String transmissionMethod() { return "SFTP"; }

    /**
     * NIBSS settlement filename: INSTITUTION_CODE + YYYYMMDD + .set
     * Example: CBA00120261130.set
     */
    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        String participantId = resolveParticipantId();
        String date = exportDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return participantId + date + ".set";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("[STUB] VerveNibssExporter.export() called: {} records for batch={} date={}",
                records.size(), batch.getBatchRef(), exportDate);

        StringBuilder sb = new StringBuilder();
        sb.append("=== VERVE/NIBSS E-SETTLEMENT STUB FILE — NOT FOR PRODUCTION ===\n");
        sb.append("Batch: ").append(batch.getBatchRef()).append("\n");
        sb.append("Settlement Date: ").append(exportDate).append("\n");
        sb.append("Record Count: ").append(records.size()).append("\n\n");
        sb.append("FORMAT: NIBSS e-settlement delimited flat file\n\n");
        sb.append("EXPECTED FIELDS (per NIBSS spec):\n");
        sb.append("  InstitutionCode | SessionID | TerminalID | RRN\n");
        sb.append("  | PAN (masked) | Amount (kobo) | ResponseCode\n");
        sb.append("  | MerchantID | TransactionDate (YYYYMMDDHHMMSS)\n\n");
        sb.append("DATA RECORDS:\n");
        for (SettlementExportRecord r : records) {
            sb.append(String.format("  pan=%s stan=%s rrn=%s auth=%s amount=%s mcc=%s%n",
                    r.maskedPan(), r.stan(), r.rrn(), r.authCode(),
                    r.grossAmount(), r.mcc()));
        }
        sb.append("\n=== END OF STUB FILE ===\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String resolveParticipantId() {
        String pid = props.forScheme("verve").getParticipantId();
        return (pid != null && !pid.isBlank()) ? pid : props.getMemberId();
    }
}

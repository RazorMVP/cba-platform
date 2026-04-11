package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Visa BASE II clearinghouse file exporter.
 *
 * <h3>Stub status</h3>
 * The {@link #export} method produces a human-readable placeholder that
 * documents the required field layout. Replace the stub body with the
 * real BASE II serializer once the Visa Clearing specification document
 * (provided under NDA on Visa scheme membership) is available.
 *
 * <h3>BASE II format overview (from public Visa documentation)</h3>
 * <ul>
 *   <li>Fixed-width records: header (1 record) + transaction records (N) + trailer (1 record)</li>
 *   <li>Standard record length: 250 bytes (some extended formats use 1024 bytes)</li>
 *   <li>Character encoding: EBCDIC on mainframe; ASCII on modern implementations</li>
 *   <li>Record type indicator: 1 byte at position 0 (H=header, D=data, T=trailer)</li>
 *   <li>Transmission: SFTP to VisaNet ({@code visanet.visa.com}) using mutual TLS key</li>
 * </ul>
 *
 * <h3>Key BASE II data fields (from public Visa spec outline)</h3>
 * <pre>
 *   Position  Len  Field
 *   0         1    Record type indicator (H/D/T)
 *   1         6    Acquiring BIN
 *   7         13   PAN (right-padded to 19)
 *   26        4    Expiry date (YYMM)
 *   30        6    Transaction date (YYMMDD)
 *   36        12   Transaction amount (minor units, zero-padded)
 *   48        3    Currency code (ISO 4217 numeric)
 *   51        6    STAN (DE11)
 *   57        12   RRN (DE37)
 *   69        6    Authorization code (DE38)
 *   75        4    MCC (DE18)
 *   79        15   Merchant ID (DE42)
 *   ...        *   Additional BASE II private fields (spec required)
 * </pre>
 *
 * <h3>To complete this implementation</h3>
 * <ol>
 *   <li>Obtain Visa BASE II Clearing specification from your Visa relationship manager</li>
 *   <li>Implement fixed-width record writer using the field table above</li>
 *   <li>Set {@code card.settlement.export.schemes.visa.enabled=true} in application.yml</li>
 *   <li>Configure SFTP credentials in environment variables</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisaBase2Exporter implements SettlementFileExporter {

    private final SettlementExportProperties props;

    @Override
    public String getScheme() { return "VISA"; }

    @Override
    public boolean isEnabled() {
        return props.forScheme("visa").isEnabled();
    }

    @Override
    public String transmissionMethod() { return "SFTP"; }

    /**
     * Generates the Visa BASE II filename.
     * Format: V + acquirerBIN(6) + YYMMDD + sequence(3) — total 12 chars.
     * Example: V04111120261130001
     */
    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        String bin = props.getAcquirerBin();
        String date = exportDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        // Sequence 001 per batch — for multiple files per day, increment per call
        return "V" + bin + date + "001";
    }

    /**
     * STUB — produces a human-readable field-layout document.
     * Replace this entire method body with the real BASE II serializer.
     */
    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("[STUB] VisaBase2Exporter.export() called: {} records for batch={} date={}",
                records.size(), batch.getBatchRef(), exportDate);

        StringBuilder sb = new StringBuilder();
        sb.append("=== VISA BASE II STUB FILE — NOT FOR PRODUCTION ===\n");
        sb.append("Batch: ").append(batch.getBatchRef()).append("\n");
        sb.append("Settlement Date: ").append(exportDate).append("\n");
        sb.append("Record Count: ").append(records.size()).append("\n\n");
        sb.append("FIELD LAYOUT (BASE II — awaiting Visa spec):\n");
        sb.append("Pos  Len  Field\n");
        sb.append("0    1    Record type (H/D/T)\n");
        sb.append("1    6    Acquiring BIN\n");
        sb.append("7    19   PAN (right-padded)\n");
        sb.append("26   4    Expiry date YYMM\n");
        sb.append("30   6    Transaction date YYMMDD\n");
        sb.append("36   12   Transaction amount (minor units)\n");
        sb.append("48   3    Currency code (ISO 4217 numeric)\n");
        sb.append("51   6    STAN\n");
        sb.append("57   12   RRN\n");
        sb.append("69   6    Authorization code\n");
        sb.append("75   4    MCC\n");
        sb.append("79   15   Merchant ID\n");
        sb.append("...  *    Additional BASE II fields per Visa spec\n\n");
        sb.append("DATA RECORDS:\n");
        for (SettlementExportRecord r : records) {
            sb.append(String.format("  pan=%s stan=%s rrn=%s auth=%s amount=%s ccy=%s mcc=%s%n",
                    r.maskedPan(), r.stan(), r.rrn(), r.authCode(),
                    r.grossAmount(), r.currencyCode(), r.mcc()));
        }
        sb.append("\n=== END OF STUB FILE ===\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}

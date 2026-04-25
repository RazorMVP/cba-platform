package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Visa BASE II clearinghouse file exporter.
 *
 * Produces fixed-width 250-byte ASCII records:
 *   Header  (record type 'H') — batch metadata
 *   Data    (record type 'D') — one per transaction
 *   Trailer (record type 'T') — record count + total amount
 *
 * Field layout mirrors the public Visa Clearing & Settlement specification.
 * Transmission: SFTP to VisaNet using mutual-TLS key (configure in application.yml).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisaBase2Exporter implements SettlementFileExporter {

    private static final int RECORD_LEN = 250;

    private final SettlementExportProperties props;

    @Override public String getScheme() { return "VISA"; }
    @Override public boolean isEnabled() { return props.forScheme("visa").isEnabled(); }
    @Override public String transmissionMethod() { return "SFTP"; }

    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        String bin  = props.getAcquirerBin();
        String date = exportDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        return "V" + bin + date + "001";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("VisaBase2Exporter: exporting {} records for batch={}", records.size(), batch.getBatchRef());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Header
        try {
            out.write(buildRecord('H',
                    pad(props.getAcquirerBin(), 6),
                    pad(exportDate.format(DateTimeFormatter.ofPattern("yyMMdd")), 6),
                    pad(batch.getBatchRef(), 20),
                    pad(String.valueOf(records.size()), 6)));

            long totalMinor = records.stream()
                    .mapToLong(r -> r.grossAmount() != null ? r.grossAmount().longValue() : 0L)
                    .sum();

            for (SettlementExportRecord r : records) {
                String pan = r.pan() != null ? r.pan() : "";
                out.write(buildRecord('D',
                        pad(props.getAcquirerBin(), 6),
                        pad(pan, 19),
                        "    ",                                                         // expiry YYMM — not in record
                        pad(formatDate(r.transactionDate()), 6),
                        padLeft(r.grossAmount() != null ? r.grossAmount().toPlainString().replace(".", "") : "0", 12, '0'),
                        pad(r.currencyCode() != null ? r.currencyCode() : "840", 3),
                        pad(r.stan() != null ? r.stan() : "", 6),
                        pad(r.rrn() != null ? r.rrn() : "", 12),
                        pad(r.authCode() != null ? r.authCode() : "", 6),
                        pad(r.mcc() != null ? r.mcc() : "0000", 4),
                        pad(r.merchantId() != null ? r.merchantId() : "", 15),
                        pad(r.merchantName() != null ? r.merchantName() : "", 25)));
            }

            // Trailer
            out.write(buildRecord('T',
                    padLeft(String.valueOf(records.size()), 6, '0'),
                    padLeft(String.valueOf(totalMinor), 12, '0')));

        } catch (IOException e) {
            throw new IllegalStateException("BASE II export failed", e);
        }
        return out.toByteArray();
    }

    private byte[] buildRecord(char type, String... fields) {
        StringBuilder sb = new StringBuilder(RECORD_LEN);
        sb.append(type);
        for (String f : fields) sb.append(f);
        // Pad to record length
        while (sb.length() < RECORD_LEN) sb.append(' ');
        return sb.substring(0, RECORD_LEN).getBytes(StandardCharsets.US_ASCII);
    }

    private String pad(String value, int len) {
        if (value == null) value = "";
        if (value.length() >= len) return value.substring(0, len);
        return value + " ".repeat(len - value.length());
    }

    private String padLeft(String value, int len, char ch) {
        if (value == null) value = "";
        if (value.length() >= len) return value.substring(value.length() - len);
        return String.valueOf(ch).repeat(len - value.length()) + value;
    }

    private String formatDate(LocalDate d) {
        return d != null ? d.format(DateTimeFormatter.ofPattern("yyMMdd")) : "000000";
    }
}

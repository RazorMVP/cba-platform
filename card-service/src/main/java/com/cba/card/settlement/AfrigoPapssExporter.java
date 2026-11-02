package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Afrigo / PAPSS (Pan-African Payment and Settlement System) clearinghouse file exporter.
 *
 * PAPSS uses a REST/HTTPS submission model with a JSON payload containing a
 * settlement batch envelope. This exporter produces the JSON byte payload; the
 * {@link SettlementFileTransmitter} posts it via HTTPS to the PAPSS clearing endpoint.
 *
 * {@code totalAmount} and each {@code amount} are whole ISO 4217 minor units. The payload hasn't been
 * checked against the PAPSS specification yet — confirm the shape and the amount unit before enabling.
 *
 * Transmission: HTTPS POST to PAPSS clearing API (configure endpoint in application.yml).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfrigoPapssExporter implements SettlementFileExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SettlementExportProperties props;

    @Override public String getScheme() { return "AFRIGO"; }
    @Override public boolean isEnabled() { return props.forScheme("afrigo").isEnabled(); }
    @Override public String transmissionMethod() { return "HTTPS"; }

    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        return props.getAcquirerBin() + "_" + exportDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "_settlement.json";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("AfrigoPapssExporter: exporting {} records for batch={}", records.size(), batch.getBatchRef());

        long totalMinor = SettlementAmounts.totalMinor(records);
        String currency = records.isEmpty() ? "" : SettlementAmounts.currency(records.get(0));

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"batchRef\": \"").append(esc(batch.getBatchRef())).append("\",\n");
        json.append("  \"settlementDate\": \"").append(exportDate.format(DATE_FMT)).append("\",\n");
        json.append("  \"participantId\": \"").append(esc(props.getAcquirerBin())).append("\",\n");
        json.append("  \"totalRecords\": ").append(records.size()).append(",\n");
        json.append("  \"totalAmount\": ").append(totalMinor).append(",\n");
        json.append("  \"currency\": \"").append(esc(currency)).append("\",\n");
        json.append("  \"transactions\": [\n");

        for (int i = 0; i < records.size(); i++) {
            SettlementExportRecord r = records.get(i);
            json.append("    {\n");
            json.append("      \"pan\": \"").append(esc(r.pan() != null ? r.pan() : "")).append("\",\n");
            json.append("      \"processingCode\": \"").append(esc(r.processingCode() != null ? r.processingCode() : "000000")).append("\",\n");
            json.append("      \"amount\": ").append(SettlementAmounts.minor(r.grossAmount())).append(",\n");
            json.append("      \"stan\": \"").append(esc(r.stan() != null ? r.stan() : "")).append("\",\n");
            json.append("      \"rrn\": \"").append(esc(r.rrn() != null ? r.rrn() : "")).append("\",\n");
            json.append("      \"authCode\": \"").append(esc(r.authCode() != null ? r.authCode() : "")).append("\",\n");
            json.append("      \"merchantId\": \"").append(esc(r.merchantId() != null ? r.merchantId() : "")).append("\",\n");
            json.append("      \"mcc\": \"").append(esc(r.mcc() != null ? r.mcc() : "0000")).append("\",\n");
            json.append("      \"transactionDate\": \"").append(r.transactionDate() != null ? r.transactionDate().format(DATE_FMT) : "").append("\",\n");
            json.append("      \"currencyCode\": \"").append(esc(SettlementAmounts.currency(r))).append("\"\n");
            json.append("    }").append(i < records.size() - 1 ? "," : "").append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Minimal JSON string escaping for field values. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

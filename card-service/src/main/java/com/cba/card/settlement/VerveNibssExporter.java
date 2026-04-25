package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

/**
 * Verve (Interswitch) / NIBSS e-Settlement clearinghouse file exporter.
 *
 * Produces a pipe-delimited flat file with a header row followed by one
 * transaction row per record. The NIBSS e-Settlement format is documented
 * in the NIBSS Electronic Financial Services Operational Framework.
 *
 * Header row columns:
 *   InstitutionCode|SettlementDate|TotalRecords|TotalAmount|Currency
 *
 * Data row columns:
 *   SequenceNo|PAN|ProcessingCode|Amount|STAN|RRN|AuthCode|
 *   MerchantId|TerminalId|MCC|TransactionDate|CurrencyCode
 *
 * Transmission: SFTP to NIBSS e-Settlement gateway.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerveNibssExporter implements SettlementFileExporter {

    private static final String DELIMITER = "|";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SettlementExportProperties props;

    @Override public String getScheme() { return "VERVE"; }
    @Override public boolean isEnabled() { return props.forScheme("verve").isEnabled(); }
    @Override public String transmissionMethod() { return "SFTP"; }

    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        return props.getAcquirerBin() + exportDate.format(DATE_FMT) + ".set";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("VerveNibssExporter: exporting {} records for batch={}", records.size(), batch.getBatchRef());

        long totalMinor = records.stream()
                .mapToLong(r -> r.grossAmount() != null ? r.grossAmount().longValue() : 0L)
                .sum();

        String currency = records.isEmpty() ? "566"
                : (records.get(0).currencyCode() != null ? records.get(0).currencyCode() : "566");

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(join(
                props.getAcquirerBin(),
                exportDate.format(DATE_FMT),
                String.valueOf(records.size()),
                String.valueOf(totalMinor),
                currency)).append("\n");

        // Data rows
        int seq = 1;
        for (SettlementExportRecord r : records) {
            sb.append(join(
                    String.valueOf(seq++),
                    r.pan() != null ? r.pan() : "",
                    r.processingCode() != null ? r.processingCode() : "000000",
                    r.grossAmount() != null ? r.grossAmount().toPlainString() : "0",
                    r.stan() != null ? r.stan() : "",
                    r.rrn() != null ? r.rrn() : "",
                    r.authCode() != null ? r.authCode() : "",
                    r.merchantId() != null ? r.merchantId() : "",
                    r.terminalId() != null ? r.terminalId() : "",
                    r.mcc() != null ? r.mcc() : "0000",
                    r.transactionDate() != null ? r.transactionDate().format(DATE_FMT) : "",
                    r.currencyCode() != null ? r.currencyCode() : currency
            )).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String join(String... values) {
        StringJoiner sj = new StringJoiner(DELIMITER);
        for (String v : values) sj.add(v != null ? v : "");
        return sj.toString();
    }
}

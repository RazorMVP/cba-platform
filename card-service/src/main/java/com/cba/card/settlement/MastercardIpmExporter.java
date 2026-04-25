package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mastercard IPM (Integrated Product Messages) clearinghouse file exporter.
 *
 * Produces length-framed ISO 8583 MTI 1240 Financial Transaction Advice records.
 * Each record is prefixed with a 2-byte big-endian message length.
 *
 * Transmission: SFTP to Mastercard GCMS (configure credentials in application.yml).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MastercardIpmExporter implements SettlementFileExporter {

    private final SettlementExportProperties props;

    @Override public String getScheme() { return "MASTERCARD"; }
    @Override public boolean isEnabled() { return props.forScheme("mastercard").isEnabled(); }
    @Override public String transmissionMethod() { return "SFTP"; }

    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        String date = exportDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        return props.getAcquirerBin() + date + "001.IPM";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("MastercardIpmExporter: exporting {} records for batch={}", records.size(), batch.getBatchRef());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (SettlementExportRecord r : records) {
                byte[] msg = buildIpm1240(r);
                // 2-byte big-endian length prefix
                out.write((msg.length >> 8) & 0xFF);
                out.write(msg.length & 0xFF);
                out.write(msg);
            }
        } catch (IOException e) {
            throw new IllegalStateException("IPM export failed", e);
        }
        return out.toByteArray();
    }

    private byte[] buildIpm1240(SettlementExportRecord r) {
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        try {
            // MTI: 1240
            msg.write("1240".getBytes(StandardCharsets.US_ASCII));

            // Primary bitmap covering DE 2,3,4,11,12,13,37,38,41,42,43,49
            long bitmap = 0L;
            for (int de : new int[]{2, 3, 4, 11, 12, 13, 37, 38, 41, 42, 43, 49}) {
                bitmap |= (1L << (64 - de));
            }
            msg.write(ByteBuffer.allocate(8).putLong(bitmap).array());

            // DE 2: PAN — LLVAR (2-digit length prefix)
            String pan = r.pan() != null ? r.pan() : "0000000000000000";
            msg.write(String.format("%02d%s", pan.length(), pan).getBytes(StandardCharsets.US_ASCII));

            // DE 3: Processing Code — 6 digits fixed
            msg.write(pad(r.processingCode() != null ? r.processingCode() : "000000", 6)
                    .getBytes(StandardCharsets.US_ASCII));

            // DE 4: Amount — 12 digits fixed (minor units)
            String amt = r.grossAmount() != null
                    ? padLeft(r.grossAmount().toPlainString().replace(".", ""), 12, '0') : "000000000000";
            msg.write(amt.getBytes(StandardCharsets.US_ASCII));

            // DE 11: STAN — 6 digits fixed
            msg.write(padLeft(r.stan() != null ? r.stan() : "000000", 6, '0')
                    .getBytes(StandardCharsets.US_ASCII));

            // DE 12: Local Time — 6 digits (HHmmss)
            msg.write("000000".getBytes(StandardCharsets.US_ASCII));

            // DE 13: Local Date — 4 digits (MMDD)
            String mmdd = r.transactionDate() != null
                    ? r.transactionDate().format(DateTimeFormatter.ofPattern("MMdd")) : "0101";
            msg.write(mmdd.getBytes(StandardCharsets.US_ASCII));

            // DE 37: RRN — 12 chars fixed
            msg.write(pad(r.rrn() != null ? r.rrn() : "", 12).getBytes(StandardCharsets.US_ASCII));

            // DE 38: Auth code — 6 chars fixed
            msg.write(pad(r.authCode() != null ? r.authCode() : "", 6).getBytes(StandardCharsets.US_ASCII));

            // DE 41: Terminal ID — 8 chars fixed
            msg.write(pad(r.terminalId() != null ? r.terminalId() : "", 8).getBytes(StandardCharsets.US_ASCII));

            // DE 42: Merchant ID — 15 chars fixed
            msg.write(pad(r.merchantId() != null ? r.merchantId() : "", 15).getBytes(StandardCharsets.US_ASCII));

            // DE 43: Merchant name — 40 chars fixed
            msg.write(pad(r.merchantName() != null ? r.merchantName() : "", 40).getBytes(StandardCharsets.US_ASCII));

            // DE 49: Currency code — 3 chars fixed
            msg.write(pad(r.currencyCode() != null ? r.currencyCode() : "840", 3)
                    .getBytes(StandardCharsets.US_ASCII));

        } catch (IOException e) {
            throw new IllegalStateException("IPM record build failed", e);
        }
        return msg.toByteArray();
    }

    private String pad(String v, int len) {
        if (v == null) v = "";
        if (v.length() >= len) return v.substring(0, len);
        return v + " ".repeat(len - v.length());
    }

    private String padLeft(String v, int len, char ch) {
        if (v == null) v = "";
        if (v.length() >= len) return v.substring(v.length() - len);
        return String.valueOf(ch).repeat(len - v.length()) + v;
    }
}

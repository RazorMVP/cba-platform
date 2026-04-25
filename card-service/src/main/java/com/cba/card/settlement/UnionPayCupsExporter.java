package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * China UnionPay CUPS (CUP Settlement) clearinghouse file exporter.
 *
 * Produces fixed-width records encoded in GB18030 (the Chinese national standard
 * superset of GBK/GB2312) to support CJK merchant names in DE43.
 *
 * Record layout (each record is 300 bytes in GB18030 encoding):
 *   Position  Len  Field
 *   0         1    Record type (H/D/T)
 *   1         8    Participant ID (acquirer BIN padded to 8)
 *   9         19   PAN (right-padded)
 *   28        6    Transaction date (yyyyMMdd truncated to 6 = yyMMdd)
 *   34        12   Transaction amount (minor units, zero-padded)
 *   46        3    Currency code (ISO 4217 numeric)
 *   49        6    STAN (DE11)
 *   55        12   RRN (DE37)
 *   67        6    Authorization code (DE38)
 *   73        4    MCC (DE18)
 *   77        15   Merchant ID (DE42)
 *   92        40   Merchant name (DE43) — GB18030 encoded, multi-byte for CJK
 *   132       8    Terminal ID (DE41)
 *   140       160  Reserved / padding
 *
 * Transmission: SFTP to CUPS gateway (configure credentials in application.yml).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnionPayCupsExporter implements SettlementFileExporter {

    private static final int RECORD_LEN = 300;
    private static final Charset GB18030;

    static {
        Charset cs;
        try {
            cs = Charset.forName("GB18030");
        } catch (Exception e) {
            cs = Charset.forName("UTF-8"); // fallback for environments without GB18030
        }
        GB18030 = cs;
    }

    private final SettlementExportProperties props;

    @Override public String getScheme() { return "UNIONPAY"; }
    @Override public boolean isEnabled() { return props.forScheme("unionpay").isEnabled(); }
    @Override public String transmissionMethod() { return "SFTP"; }

    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        String date = exportDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return props.getAcquirerBin() + date + ".cup";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("UnionPayCupsExporter: exporting {} records for batch={}", records.size(), batch.getBatchRef());

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            // Header record
            out.write(buildRecord('H',
                    padAscii(props.getAcquirerBin(), 8),
                    padAscii(exportDate.format(DateTimeFormatter.ofPattern("yyMMdd")), 6),
                    padAscii(String.valueOf(records.size()), 10)));

            long totalMinor = records.stream()
                    .mapToLong(r -> r.grossAmount() != null ? r.grossAmount().longValue() : 0L)
                    .sum();

            for (SettlementExportRecord r : records) {
                out.write(buildRecord('D',
                        padAscii(props.getAcquirerBin(), 8),
                        padAscii(r.pan() != null ? r.pan() : "", 19),
                        padAscii(r.transactionDate() != null
                                ? r.transactionDate().format(DateTimeFormatter.ofPattern("yyMMdd")) : "000000", 6),
                        padLeftAscii(r.grossAmount() != null
                                ? r.grossAmount().toPlainString().replace(".", "") : "0", 12, '0'),
                        padAscii(r.currencyCode() != null ? r.currencyCode() : "156", 3),
                        padAscii(r.stan() != null ? r.stan() : "", 6),
                        padAscii(r.rrn() != null ? r.rrn() : "", 12),
                        padAscii(r.authCode() != null ? r.authCode() : "", 6),
                        padAscii(r.mcc() != null ? r.mcc() : "0000", 4),
                        padAscii(r.merchantId() != null ? r.merchantId() : "", 15),
                        padGb18030(r.merchantName() != null ? r.merchantName() : "", 40),
                        padAscii(r.terminalId() != null ? r.terminalId() : "", 8)));
            }

            // Trailer record
            out.write(buildRecord('T',
                    padLeftAscii(String.valueOf(records.size()), 8, '0'),
                    padLeftAscii(String.valueOf(totalMinor), 12, '0')));

        } catch (java.io.IOException e) {
            throw new IllegalStateException("CUPS export failed", e);
        }
        return out.toByteArray();
    }

    private byte[] buildRecord(char type, byte[]... fields) {
        byte[] record = new byte[RECORD_LEN];
        java.util.Arrays.fill(record, (byte) 0x20); // space-fill
        record[0] = (byte) type;
        int pos = 1;
        for (byte[] f : fields) {
            int len = Math.min(f.length, RECORD_LEN - pos);
            System.arraycopy(f, 0, record, pos, len);
            pos += len;
            if (pos >= RECORD_LEN) break;
        }
        return record;
    }

    private byte[] padAscii(String value, int len) {
        if (value == null) value = "";
        byte[] src = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] out = new byte[len];
        java.util.Arrays.fill(out, (byte) 0x20);
        System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        return out;
    }

    private byte[] padLeftAscii(String value, int len, char pad) {
        if (value == null) value = "";
        while (value.length() < len) value = pad + value;
        if (value.length() > len) value = value.substring(value.length() - len);
        return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /** Encode merchant name in GB18030, truncated/padded to exactly {@code len} bytes. */
    private byte[] padGb18030(String value, int len) {
        if (value == null) value = "";
        byte[] encoded = value.getBytes(GB18030);
        byte[] out = new byte[len];
        java.util.Arrays.fill(out, (byte) 0x20);
        System.arraycopy(encoded, 0, out, 0, Math.min(encoded.length, len));
        return out;
    }
}

package com.cba.card.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Format tests for the four non-Visa settlement exporters: Mastercard IPM,
 * Verve/NIBSS, Afrigo/PAPSS, UnionPay CUPS. Each asserts its scheme metadata
 * and the structural contract its clearinghouse rejects on if wrong.
 */
@ExtendWith(MockitoExtension.class)
class SchemeExportersTest {

    @Mock SettlementExportProperties props;

    private static final LocalDate DATE = LocalDate.of(2026, 6, 20);

    private static SettlementExportRecord rec(String pan, long grossMinor, String merchantName) {
        BigDecimal gross = BigDecimal.valueOf(grossMinor);
        return new SettlementExportRecord(
                UUID.randomUUID(), UUID.randomUUID(),
                SettlementExportRecord.maskPan(pan), pan, "DEBIT", "X",
                "0100", "000123", "RRN000000001", "AUTH01", "000000", "CHIP",
                gross, BigDecimal.ZERO, BigDecimal.ZERO, gross, "566",
                "MERCH01", merchantName, "5411", "TERM0001", "412345",
                DATE, DATE);
    }

    private static SettlementBatch batch(String ref) {
        SettlementBatch b = new SettlementBatch();
        b.setBatchRef(ref);
        return b;
    }

    // ── Mastercard IPM (length-framed ISO 8583 MTI 1240) ───────────────────────

    @Test
    @DisplayName("Mastercard IPM: metadata + 2-byte length-framed MTI 1240 records")
    void mastercardIpm() {
        MastercardIpmExporter ex = new MastercardIpmExporter(props);
        assertThat(ex.getScheme()).isEqualTo("MASTERCARD");
        assertThat(ex.transmissionMethod()).isEqualTo("SFTP");

        when(props.getAcquirerBin()).thenReturn("412345");
        assertThat(ex.generateFileName(batch("B1"), DATE)).isEqualTo("412345260620001.IPM");

        byte[] out = ex.export(List.of(rec("5111111111111118", 10000, "Shop")), batch("B1"), DATE);
        int len = ((out[0] & 0xFF) << 8) | (out[1] & 0xFF);   // 2-byte big-endian prefix
        assertThat(out).hasSize(2 + len);                      // single record
        assertThat(new String(out, 2, 4, StandardCharsets.US_ASCII)).isEqualTo("1240"); // MTI
    }

    // ── Verve / NIBSS (pipe-delimited flat file) ───────────────────────────────

    @Test
    @DisplayName("Verve/NIBSS: pipe-delimited header + one data row per record")
    void verveNibss() {
        VerveNibssExporter ex = new VerveNibssExporter(props);
        assertThat(ex.getScheme()).isEqualTo("VERVE");
        assertThat(ex.transmissionMethod()).isEqualTo("SFTP");

        when(props.getAcquirerBin()).thenReturn("506099");
        assertThat(ex.generateFileName(batch("B1"), DATE)).isEqualTo("50609920260620.set");

        String out = new String(ex.export(List.of(rec("5060991111111118", 10000, "Shop")), batch("B1"), DATE),
                StandardCharsets.UTF_8);
        String[] lines = out.strip().split("\n");
        assertThat(lines).hasSize(2);                                   // header + 1 data row
        String[] header = lines[0].split("\\|");
        assertThat(header[0]).isEqualTo("506099");                      // institution code
        assertThat(header[2]).isEqualTo("1");                           // total records
        assertThat(lines[1]).startsWith("1|").contains("5060991111111118"); // seq 1 + PAN
    }

    // ── Afrigo / PAPSS (JSON over HTTPS) ───────────────────────────────────────

    @Test
    @DisplayName("Afrigo/PAPSS: JSON envelope over HTTPS, with escaping")
    void afrigoPapss() {
        AfrigoPapssExporter ex = new AfrigoPapssExporter(props);
        assertThat(ex.getScheme()).isEqualTo("AFRIGO");
        assertThat(ex.transmissionMethod()).isEqualTo("HTTPS"); // not SFTP

        when(props.getAcquirerBin()).thenReturn("506100");
        assertThat(ex.generateFileName(batch("B1"), DATE)).isEqualTo("506100_20260620_settlement.json");

        // batchRef with a quote must be JSON-escaped
        String json = new String(ex.export(List.of(rec("5061001111111111", 10000, "Shop")), batch("a\"b"), DATE),
                StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"totalRecords\": 1")
                .contains("\"transactions\"")
                .contains("\"batchRef\": \"a\\\"b\"")   // escaped quote
                .contains("5061001111111111");
    }

    // ── UnionPay CUPS (300-byte GB18030 fixed-width) ───────────────────────────

    @Test
    @DisplayName("UnionPay CUPS: 300-byte H/D/T records, GB18030 CJK merchant name")
    void unionPayCups() {
        UnionPayCupsExporter ex = new UnionPayCupsExporter(props);
        assertThat(ex.getScheme()).isEqualTo("UNIONPAY"); // normalised (matches exporter routing)
        assertThat(ex.transmissionMethod()).isEqualTo("SFTP");

        when(props.getAcquirerBin()).thenReturn("62123456");
        assertThat(ex.generateFileName(batch("B1"), DATE)).isEqualTo("6212345620260620.cup");

        // CJK merchant name exercises the GB18030 encoding path
        byte[] out = ex.export(List.of(rec("6212341111111111", 10000, "测试商户")), batch("B1"), DATE);
        assertThat(out).hasSize(3 * 300);            // H + D + T
        assertThat((char) out[0]).isEqualTo('H');
        assertThat((char) out[300]).isEqualTo('D');
        assertThat((char) out[600]).isEqualTo('T');
    }
}

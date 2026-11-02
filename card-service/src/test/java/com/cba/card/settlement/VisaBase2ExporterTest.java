package com.cba.card.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link VisaBase2Exporter} — the Visa BASE II fixed-width (250-byte)
 * clearing-file format. Wrong record framing / totals = a rejected settlement file.
 */
@ExtendWith(MockitoExtension.class)
class VisaBase2ExporterTest {

    private static final int RECORD_LEN = 250;

    @Mock SettlementExportProperties props;
    @InjectMocks VisaBase2Exporter exporter;

    /** Gross in minor units, at scale 4 — the shape {@code settlement_items.amount} (NUMERIC(19,4)) comes back in. */
    private static SettlementExportRecord rec(String pan, long grossMinor) {
        return rec(pan, BigDecimal.valueOf(grossMinor).setScale(4), "840");
    }

    private static SettlementExportRecord rec(String pan, BigDecimal gross, String currency) {
        return new SettlementExportRecord(
                UUID.randomUUID(), UUID.randomUUID(),
                SettlementExportRecord.maskPan(pan), pan, "DEBIT", "VISA",
                "0100", "000001", "RRN000000001", "AUTH01", "000000", "CHIP",
                gross, BigDecimal.ZERO, BigDecimal.ZERO, gross, currency,
                "MERCH01", "Test Shop", "5411", "TERM01", "412345",
                LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 19));
    }

    private static SettlementBatch batch() {
        SettlementBatch b = new SettlementBatch();
        b.setBatchRef("BATCH-0001");
        return b;
    }

    @Test
    @DisplayName("metadata: scheme VISA, SFTP transmission")
    void metadata() {
        assertThat(exporter.getScheme()).isEqualTo("VISA");
        assertThat(exporter.transmissionMethod()).isEqualTo("SFTP");
    }

    @Test
    @DisplayName("file name is V + acquirerBin + yyMMdd + 001")
    void fileName() {
        when(props.getAcquirerBin()).thenReturn("412345");
        assertThat(exporter.generateFileName(batch(), LocalDate.of(2026, 6, 19)))
                .isEqualTo("V412345260619001");
    }

    @Test
    @DisplayName("export emits a 250-byte H + one D per record + T, all fixed-width")
    void recordFraming() {
        when(props.getAcquirerBin()).thenReturn("412345");
        byte[] out = exporter.export(List.of(rec("4111111111111111", 10000), rec("4222222222222222", 5000)),
                batch(), LocalDate.of(2026, 6, 19));

        assertThat(out).hasSize(4 * RECORD_LEN);          // H + D + D + T
        assertThat((char) out[0]).isEqualTo('H');
        assertThat((char) out[RECORD_LEN]).isEqualTo('D');
        assertThat((char) out[2 * RECORD_LEN]).isEqualTo('D');
        assertThat((char) out[3 * RECORD_LEN]).isEqualTo('T');
    }

    @Test
    @DisplayName("trailer carries the record count and summed gross (minor units)")
    void trailerTotals() {
        when(props.getAcquirerBin()).thenReturn("412345");
        byte[] out = exporter.export(List.of(rec("4111111111111111", 10000), rec("4222222222222222", 5000)),
                batch(), LocalDate.of(2026, 6, 19));

        // Trailer: 'T' + count(6,'0') + total(12,'0')  → 2 records, 15000 total
        String trailer = new String(out, 3 * RECORD_LEN, 19, StandardCharsets.US_ASCII);
        assertThat(trailer).isEqualTo("T000002000000015000");
    }

    @Test
    @DisplayName("data record amount is the minor units from a scale-4 column, not ×10⁴ (backlog 21)")
    void dataRecordAmount() {
        when(props.getAcquirerBin()).thenReturn("412345");
        byte[] out = exporter.export(List.of(rec("4111111111111111", 2500)), batch(), LocalDate.of(2026, 6, 19));

        // 'D' + BIN(6) + PAN(19) + expiry(4) + date(6) = 36, then amount(12) + currency(3)
        String data = new String(out, RECORD_LEN, RECORD_LEN, StandardCharsets.US_ASCII);
        assertThat(data.substring(36, 48)).isEqualTo("000000002500");    // 25.00 — was 000025000000
        assertThat(data.substring(48, 51)).isEqualTo("840");
        // the trailer total agrees with the data record
        assertThat(new String(out, 2 * RECORD_LEN, 19, StandardCharsets.US_ASCII)).endsWith("000000002500");
    }

    @Test
    @DisplayName("refuses what it can't write exactly: fractional minor units, no currency, too many digits")
    void refusesInexactAmounts() {
        when(props.getAcquirerBin()).thenReturn("412345");
        LocalDate d = LocalDate.of(2026, 6, 19);
        assertThatThrownBy(() -> exporter.export(
                List.of(rec("4111111111111111", new BigDecimal("2500.5000"), "840")), batch(), d))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minor units");
        assertThatThrownBy(() -> exporter.export(
                List.of(rec("4111111111111111", new BigDecimal("2500.0000"), null)), batch(), d))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no currency");
        assertThatThrownBy(() -> exporter.export(
                List.of(rec("4111111111111111", new BigDecimal("1234567890123"), "840")), batch(), d))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("doesn't fit 12 digits");
    }

    @Test
    @DisplayName("maskPan keeps first 6 + last 4, masks the middle")
    void maskPan() {
        assertThat(SettlementExportRecord.maskPan("4111111111111111")).isEqualTo("411111******1111");
        assertThat(SettlementExportRecord.maskPan("123")).isEqualTo("****");
    }
}

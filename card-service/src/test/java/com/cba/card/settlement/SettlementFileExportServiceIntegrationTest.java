package com.cba.card.settlement;

import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers integration test for {@link SettlementFileExportService#buildExportRecords}
 * (the Gap-7 SQL path) against a real PostgreSQL. Validates that the SQL:
 * <ul>
 *   <li>resolves the scheme via a BIN range-scan and normalizes {@code UNION_PAY → UNIONPAY}
 *       (so it matches {@code UnionPayCupsExporter.getScheme()});</li>
 *   <li>produces a masked PAN (first 6 + mask + last 4), never the full PAN;</li>
 *   <li>takes interchange, scheme fee and net from the item's own minor-unit figures, not
 *       {@code interchange_log} (whose units changed at V13);</li>
 *   <li>only includes {@code SETTLED} items.</li>
 * </ul>
 *
 * <p>Runs under {@code -Pfull-integration} only (needs Docker). Also exercises the
 * full Spring context + Flyway migrations on a live DB.
 */
class SettlementFileExportServiceIntegrationTest extends AbstractCardIntegrationTest {

    @Autowired SettlementFileExportService service;
    @Autowired SettlementTransmissionRepository transmissionRepository;
    @Autowired UnionPayCupsExporter unionPayExporter;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("buildExportRecords resolves UNION_PAY→UNIONPAY, masks PAN, nets interchange, SETTLED-only")
    void buildExportRecordsFromRealSchema() {
        UUID productId = UUID.randomUUID();
        UUID cardId    = UUID.randomUUID();
        UUID authId    = UUID.randomUUID();
        UUID batchId   = UUID.randomUUID();
        UUID settledItemId = UUID.randomUUID();
        UUID pendingItemId = UUID.randomUUID();

        // ── Seed: card product + card whose 8-digit prefix falls in a UNION_PAY BIN range ──
        jdbc.update("""
                INSERT INTO card_products (id, name, card_type, bin_range_start, bin_range_end, default_daily_limit, features)
                VALUES (?, 'UP Debit', 'DEBIT', '62000000', '62999999', 100000, '{}'::jsonb)
                """, productId);
        jdbc.update("""
                INSERT INTO bin_ranges (bin_start, bin_end, scheme, product_type, card_type, country_code, active)
                VALUES ('62000000', '62999999', 'UNION_PAY', 'DEBIT', 'DEBIT', 'CHN', true)
                """);
        jdbc.update("""
                INSERT INTO cards (id, pan_encrypted, pan_hash, pan_prefix, pan_suffix, expiry_date,
                                   cvv_encrypted, card_type, status, customer_id, product_id)
                VALUES (?, 'enc', ?, '62123456', '1111', '2612', 'enc', 'DEBIT', 'ACTIVE', ?, ?)
                """, cardId, "hash-" + cardId, UUID.randomUUID(), productId);

        // ── Seed: an approved authorization + its interchange_log ──
        jdbc.update("""
                INSERT INTO authorization_log (id, card_id, stan, rrn, mti, processing_code, amount,
                                               currency_code, response_code, entry_mode, merchant_id, merchant_name, mcc)
                VALUES (?, ?, '000123', 'RRN000000001', '0100', '000000', 10000, '156', '00', 'CHIP',
                        'MERCH01', 'UP Shop', '5411')
                """, authId, cardId);
        // interchange_log mixes units across V13 (minor before, major after), so the export must not read it:
        // these values differ from the item's own figures below on purpose.
        jdbc.update("""
                INSERT INTO interchange_log (authorization_log_id, scheme, interchange_amount, scheme_fee_amount, net_settlement_amount)
                VALUES (?, 'UNIONPAY', 1.50, 0.13, 98.37)
                """, authId);

        // ── Seed: a CLOSED batch with one SETTLED item (included) + one PENDING item (excluded) ──
        jdbc.update("""
                INSERT INTO settlement_batches (id, batch_ref, status, settlement_date, total_amount, item_count)
                VALUES (?, ?, 'CLOSED', CURRENT_DATE, 10000, 1)
                """, batchId, batchId.toString());
        // The item's own figures (minor units, set at clearing since 15b); scheme left null → BIN range-scan.
        jdbc.update("""
                INSERT INTO settlement_items (id, batch_id, authorization_log_id, amount, currency_code, status,
                                              interchange_amount, scheme_fee_amount, gl_net)
                VALUES (?, ?, ?, 10000, '156', 'SETTLED', 150, 13, 9837)
                """, settledItemId, batchId, authId);
        jdbc.update("""
                INSERT INTO settlement_items (id, batch_id, authorization_log_id, amount, currency_code, status)
                VALUES (?, ?, ?, 5000, '156', 'PENDING')
                """, pendingItemId, batchId, authId);

        // ── Exercise ──
        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchId);
        List<SettlementExportRecord> records = service.buildExportRecords(batch, java.time.LocalDate.now());

        // ── Assert ──
        assertThat(records).hasSize(1); // SETTLED only — PENDING excluded
        SettlementExportRecord r = records.get(0);
        assertThat(r.scheme()).isEqualTo("UNIONPAY");                 // UNION_PAY normalized
        assertThat(r.maskedPan()).isEqualTo("621234******1111");     // first 6 + mask + last 4
        assertThat(r.pan()).isEmpty();                                // full PAN never serialized in SQL path
        assertThat(r.grossAmount()).isEqualByComparingTo("10000");
        assertThat(r.interchangeAmount()).isEqualByComparingTo("150");
        assertThat(r.schemeFeeAmount()).isEqualByComparingTo("13");
        assertThat(r.netAmount()).isEqualByComparingTo("9837");       // the item's gl_net, not interchange_log
        assertThat(r.cardType()).isEqualTo("DEBIT");
        assertThat(r.merchantName()).isEqualTo("UP Shop");

        // The column comes back at scale 4 (10000.0000); the file must carry 10000 minor units, not ×10⁴.
        assertThat(r.grossAmount().scale()).isEqualTo(4);
        byte[] file = unionPayExporter.export(records, batch, java.time.LocalDate.now());
        assertThat(new String(file, 300 + 34, 12, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("000000010000");
    }

    @Test
    @DisplayName("V14: one live transmission per batch+scheme; a FAILED one doesn't block a new claim")
    void oneLiveTransmissionPerBatchAndScheme() {
        UUID batchId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO settlement_batches (id, batch_ref, status, settlement_date, total_amount, item_count)
                VALUES (?, ?, 'CLOSED', CURRENT_DATE, 0, 0)
                """, batchId, batchId.toString());

        SettlementTransmission first = transmissionRepository.saveAndFlush(transmission(batchId));
        assertThatThrownBy(() -> transmissionRepository.saveAndFlush(transmission(batchId)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        first.setStatus("FAILED");
        transmissionRepository.saveAndFlush(first);
        SettlementTransmission retried = transmissionRepository.saveAndFlush(transmission(batchId));
        assertThat(retried.getId()).isNotEqualTo(first.getId());
        assertThat(transmissionRepository.existsByBatchIdAndScheme(batchId, "VISA")).isTrue();
    }

    private static SettlementTransmission transmission(UUID batchId) {
        SettlementTransmission tx = new SettlementTransmission();
        tx.setBatchId(batchId);
        tx.setScheme("VISA");
        tx.setFileName("V-" + batchId);
        tx.setSettlementDate(java.time.LocalDate.now());
        return tx;
    }

    @Test
    @DisplayName("a card BIN with no matching range resolves to scheme UNKNOWN")
    void unknownSchemeWhenNoBinRange() {
        UUID productId = UUID.randomUUID();
        UUID cardId    = UUID.randomUUID();
        UUID authId    = UUID.randomUUID();
        UUID batchId   = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO card_products (id, name, card_type, bin_range_start, bin_range_end, default_daily_limit, features)
                VALUES (?, 'Orphan', 'DEBIT', '70000000', '70999999', 100000, '{}'::jsonb)
                """, productId);
        // No bin_ranges row covering prefix 70123456
        jdbc.update("""
                INSERT INTO cards (id, pan_encrypted, pan_hash, pan_prefix, pan_suffix, expiry_date,
                                   cvv_encrypted, card_type, status, customer_id, product_id)
                VALUES (?, 'enc', ?, '70123456', '2222', '2612', 'enc', 'DEBIT', 'ACTIVE', ?, ?)
                """, cardId, "hash-" + cardId, UUID.randomUUID(), productId);
        jdbc.update("""
                INSERT INTO authorization_log (id, card_id, stan, mti, amount, currency_code, response_code)
                VALUES (?, ?, '000124', '0100', 2000, '840', '00')
                """, authId, cardId);
        jdbc.update("""
                INSERT INTO settlement_batches (id, batch_ref, status, settlement_date, total_amount, item_count)
                VALUES (?, ?, 'CLOSED', CURRENT_DATE, 2000, 1)
                """, batchId, batchId.toString());
        jdbc.update("""
                INSERT INTO settlement_items (id, batch_id, authorization_log_id, amount, currency_code, status)
                VALUES (?, ?, ?, 2000, '840', 'SETTLED')
                """, UUID.randomUUID(), batchId, authId);

        SettlementBatch batch = new SettlementBatch();
        batch.setId(batchId);
        List<SettlementExportRecord> records = service.buildExportRecords(batch, java.time.LocalDate.now());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).scheme()).isEqualTo("UNKNOWN");
        // net falls back to the gross amount when the item has no gl_net (e.g. cleared before 15b)
        assertThat(records.get(0).netAmount()).isEqualByComparingTo("2000");
    }
}

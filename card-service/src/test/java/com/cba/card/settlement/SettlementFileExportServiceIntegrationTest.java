package com.cba.card.settlement;

import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration test for {@link SettlementFileExportService#buildExportRecords}
 * (the Gap-7 SQL path) against a real PostgreSQL. Validates that the SQL:
 * <ul>
 *   <li>resolves the scheme via a BIN range-scan and normalizes {@code UNION_PAY → UNIONPAY}
 *       (so it matches {@code UnionPayCupsExporter.getScheme()});</li>
 *   <li>produces a masked PAN (first 6 + mask + last 4), never the full PAN;</li>
 *   <li>nets interchange via the latest {@code interchange_log} row;</li>
 *   <li>only includes {@code SETTLED} items.</li>
 * </ul>
 *
 * <p>Runs under {@code -Pfull-integration} only (needs Docker). Also exercises the
 * full Spring context + Flyway migrations on a live DB.
 */
class SettlementFileExportServiceIntegrationTest extends AbstractCardIntegrationTest {

    @Autowired SettlementFileExportService service;
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
        jdbc.update("""
                INSERT INTO interchange_log (authorization_log_id, scheme, interchange_amount, scheme_fee_amount, net_settlement_amount)
                VALUES (?, 'UNIONPAY', 150, 13, 9837)
                """, authId);

        // ── Seed: a CLOSED batch with one SETTLED item (included) + one PENDING item (excluded) ──
        jdbc.update("""
                INSERT INTO settlement_batches (id, batch_ref, status, settlement_date, total_amount, item_count)
                VALUES (?, ?, 'CLOSED', CURRENT_DATE, 10000, 1)
                """, batchId, batchId.toString());
        jdbc.update("""
                INSERT INTO settlement_items (id, batch_id, authorization_log_id, amount, currency_code, status)
                VALUES (?, ?, ?, 10000, '156', 'SETTLED')
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
        assertThat(r.netAmount()).isEqualByComparingTo("9837");       // from interchange_log
        assertThat(r.cardType()).isEqualTo("DEBIT");
        assertThat(r.merchantName()).isEqualTo("UP Shop");
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
        // net falls back to the gross amount when there is no interchange_log row
        assertThat(records.get(0).netAmount()).isEqualByComparingTo("2000");
    }
}

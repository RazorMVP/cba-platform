package com.cba.card.settlement;

import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the nightly settlement file export process.
 *
 * <h3>Nightly flow (runs at 23:58 via {@code card.settlement.export.export-cron})</h3>
 * <ol>
 *   <li>Find all CLOSED settlement batches that have not yet been successfully transmitted</li>
 *   <li>For each batch, load settlement items and enrich with authorization_log data</li>
 *   <li>Group items by scheme</li>
 *   <li>For each scheme: call the registered {@link SettlementFileExporter}, then
 *       {@link SettlementFileTransmitter}, with retry on failure</li>
 *   <li>Record each attempt in {@code settlement_transmissions}</li>
 * </ol>
 *
 * <h3>Idempotency</h3>
 * A batch+scheme combination that already has a TRANSMITTED record is skipped —
 * safe for manual re-run or cron overlap.
 *
 * <h3>Manual trigger</h3>
 * {@link #exportBatch(UUID, LocalDate)} is exposed via {@link SettlementExportController}
 * for ops staff to re-export a specific batch without waiting for the nightly cron.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementFileExportService {

    private final SettlementBatchRepository          batchRepository;
    private final SettlementItemRepository           itemRepository;
    private final SettlementTransmissionRepository   transmissionRepository;
    private final List<SettlementFileExporter>       exporters;
    private final SettlementFileTransmitter          transmitter;
    private final SettlementExportProperties         props;
    private final JdbcTemplate                       jdbc;

    // ── Nightly scheduled export ──────────────────────────────────────────────

    @Scheduled(cron = "${card.settlement.export.export-cron:0 58 23 * * *}")
    public void runNightlyExport() {
        LocalDate today = LocalDate.now();
        log.info("SettlementFileExportService nightly run for date={}", today);

        List<SettlementBatch> closedBatches = batchRepository
                .findByStatusAndSettlementDate(SettlementBatchStatus.CLOSED, today);

        if (closedBatches.isEmpty()) {
            log.info("No CLOSED batches found for date={} — nothing to export", today);
            return;
        }

        for (SettlementBatch batch : closedBatches) {
            try {
                exportBatch(batch.getId(), today);
            } catch (Exception e) {
                log.error("Batch export failed: batchId={} error={}", batch.getId(), e.getMessage());
            }
        }
    }

    // ── Core export logic ─────────────────────────────────────────────────────

    /**
     * Export all scheme files for a settlement batch.
     * Called by the nightly scheduler and by the manual REST trigger.
     *
     * @param batchId      UUID of the settlement batch to export
     * @param settlementDate the settlement date to embed in filenames and file headers
     */
    @Transactional
    public List<SettlementTransmission> exportBatch(UUID batchId, LocalDate settlementDate) {
        SettlementBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> CbaException.notFound("BATCH_NOT_FOUND",
                        "Settlement batch not found: " + batchId));

        List<SettlementExportRecord> allRecords = buildExportRecords(batch, settlementDate);

        // Group records by scheme
        Map<String, List<SettlementExportRecord>> byScheme = allRecords.stream()
                .collect(Collectors.groupingBy(SettlementExportRecord::scheme));

        List<SettlementTransmission> results = new ArrayList<>();

        for (SettlementFileExporter exporter : exporters) {
            if (!exporter.isEnabled()) {
                log.info("Exporter {} is disabled — skipping", exporter.getScheme());
                continue;
            }
            List<SettlementExportRecord> schemeRecords =
                    byScheme.getOrDefault(exporter.getScheme(), List.of());

            if (schemeRecords.isEmpty()) {
                log.info("No records for scheme={} in batch={} — skipping",
                        exporter.getScheme(), batchId);
                continue;
            }

            // Idempotency: skip if already transmitted
            boolean alreadyTransmitted = transmissionRepository
                    .findByBatchIdAndSchemeAndStatus(batchId, exporter.getScheme(), "TRANSMITTED")
                    .isPresent();
            if (alreadyTransmitted) {
                log.info("Batch {} already transmitted for scheme={} — skipping",
                        batchId, exporter.getScheme());
                continue;
            }

            SettlementTransmission tx = executeWithRetry(
                    exporter, schemeRecords, batch, settlementDate);
            results.add(tx);
        }

        return results;
    }

    // ── Retry logic ───────────────────────────────────────────────────────────

    private SettlementTransmission executeWithRetry(
            SettlementFileExporter exporter,
            List<SettlementExportRecord> records,
            SettlementBatch batch,
            LocalDate settlementDate) {

        String fileName = exporter.generateFileName(batch, settlementDate);
        String endpoint = resolveEndpoint(exporter.getScheme(), exporter.transmissionMethod());

        // Create transmission record in PENDING state
        SettlementTransmission tx = new SettlementTransmission();
        tx.setBatchId(batch.getId());
        tx.setScheme(exporter.getScheme());
        tx.setFileName(fileName);
        tx.setRecordCount(records.size());
        tx.setTotalAmount(records.stream()
                .map(SettlementExportRecord::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        tx.setSettlementDate(settlementDate);
        tx.setTransmissionMethod(exporter.transmissionMethod());
        tx.setEndpoint(endpoint);
        tx = transmissionRepository.save(tx);

        int maxRetries = props.getMaxRetries();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            tx.setAttemptCount(attempt);
            tx.setLastAttemptAt(OffsetDateTime.now());

            try {
                log.info("Exporting scheme={} attempt={}/{} records={}",
                        exporter.getScheme(), attempt, maxRetries, records.size());

                byte[] fileBytes = exporter.export(records, batch, settlementDate);
                transmitter.transmit(fileBytes, fileName, exporter.getScheme(),
                        exporter.transmissionMethod());

                tx.setStatus("TRANSMITTED");
                tx.setTransmittedAt(OffsetDateTime.now());
                tx.setErrorMessage(null);
                transmissionRepository.save(tx);

                log.info("Settlement file transmitted: scheme={} file={} records={} amount={}",
                        exporter.getScheme(), fileName, records.size(), tx.getTotalAmount());
                return tx;

            } catch (Exception e) {
                lastException = e;
                tx.setErrorMessage(e.getMessage());
                transmissionRepository.save(tx);
                log.warn("Transmission attempt {}/{} failed for scheme={}: {}",
                        attempt, maxRetries, exporter.getScheme(), e.getMessage());

                if (attempt < maxRetries) {
                    sleepRetryDelay();
                }
            }
        }

        // All retries exhausted
        tx.setStatus("FAILED");
        tx.setErrorMessage("All " + maxRetries + " attempts failed. Last: "
                + (lastException != null ? lastException.getMessage() : "unknown"));
        transmissionRepository.save(tx);
        log.error("Settlement export FAILED after {} retries: scheme={} batch={}",
                maxRetries, exporter.getScheme(), batch.getId());
        return tx;
    }

    // ── Record builder ────────────────────────────────────────────────────────

    /**
     * Build normalized {@link SettlementExportRecord} list for a batch.
     * Joins settlement_items with authorization_log to populate all fields
     * exporters need, without requiring exporters to touch the DB.
     */
    private List<SettlementExportRecord> buildExportRecords(
            SettlementBatch batch, LocalDate settlementDate) {

        // Joins settlement_items -> authorization_log -> cards (for scheme via BIN + masked PAN + card type)
        // and the latest interchange_log row (for interchange/scheme-fee/net). Scheme is resolved by a
        // BIN range-scan against the card's 8-digit prefix and normalized so it matches the exporter codes
        // (bin_ranges stores 'UNION_PAY' but UnionPayCupsExporter.getScheme() is 'UNIONPAY').
        String sql = """
                SELECT
                    si.id                  AS settlement_item_id,
                    si.authorization_log_id,
                    si.amount              AS gross_amount,
                    si.currency_code,
                    al.stan,
                    al.rrn,
                    al.mti,
                    al.processing_code,
                    al.entry_mode,
                    al.merchant_id,
                    al.merchant_name,
                    al.mcc,
                    -- authorization_log has no terminal_id column; default to empty
                    ''                     AS terminal_id,
                    -- Resolve scheme from the card BIN; normalize UNION_PAY -> UNIONPAY to match exporter code
                    COALESCE((
                        SELECT CASE WHEN br.scheme = 'UNION_PAY' THEN 'UNIONPAY' ELSE br.scheme END
                        FROM bin_ranges br
                        WHERE br.active = true
                          AND br.bin_start <= c.pan_prefix
                          AND br.bin_end   >= c.pan_prefix
                        ORDER BY LENGTH(br.bin_start) DESC
                        LIMIT 1
                    ), 'UNKNOWN')          AS scheme,
                    -- Masked PAN only (no full PAN decrypt in the SQL path): first 6 + mask + last 4
                    CASE WHEN c.pan_prefix IS NOT NULL
                         THEN SUBSTRING(c.pan_prefix FROM 1 FOR 6) || '******' || c.pan_suffix
                         ELSE '****' END   AS masked_pan,
                    COALESCE(c.card_type, 'DEBIT')              AS card_type,
                    -- auth_code stored in response_code field for approved txns
                    CASE WHEN al.response_code = '00' THEN al.rrn ELSE '' END AS auth_code,
                    al.created_at::date    AS transaction_date,
                    COALESCE(il.interchange_amount, 0)            AS interchange_amount,
                    COALESCE(il.scheme_fee_amount, 0)            AS scheme_fee_amount,
                    COALESCE(il.net_settlement_amount, si.amount) AS net_amount
                FROM settlement_items si
                LEFT JOIN authorization_log al ON al.id = si.authorization_log_id
                LEFT JOIN cards c            ON c.id  = al.card_id
                LEFT JOIN LATERAL (
                    SELECT il2.interchange_amount, il2.scheme_fee_amount, il2.net_settlement_amount
                    FROM interchange_log il2
                    WHERE il2.authorization_log_id = al.id
                    ORDER BY il2.calculated_at DESC
                    LIMIT 1
                ) il ON true
                WHERE si.batch_id = ?
                  AND si.status = 'SETTLED'
                """;

        return jdbc.query(sql, (rs, rowNum) -> new SettlementExportRecord(
                UUID.fromString(rs.getString("settlement_item_id")),
                rs.getString("authorization_log_id") != null
                        ? UUID.fromString(rs.getString("authorization_log_id")) : null,
                rs.getString("masked_pan"),
                "",                                             // pan — masked-only; full PAN decrypt deferred (Gap 7 decision)
                rs.getString("card_type"),
                rs.getString("scheme"),
                rs.getString("mti"),
                rs.getString("stan"),
                rs.getString("rrn"),
                rs.getString("auth_code"),
                rs.getString("processing_code"),
                rs.getString("entry_mode"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("interchange_amount"),
                rs.getBigDecimal("scheme_fee_amount"),
                rs.getBigDecimal("net_amount"),
                rs.getString("currency_code"),
                rs.getString("merchant_id"),
                rs.getString("merchant_name"),
                rs.getString("mcc"),
                rs.getString("terminal_id"),
                props.getAcquirerBin(),
                rs.getDate("transaction_date") != null
                        ? rs.getDate("transaction_date").toLocalDate() : settlementDate,
                settlementDate
        ), batch.getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveEndpoint(String scheme, String method) {
        SettlementExportProperties.SchemeExportConfig cfg = props.forScheme(scheme.toLowerCase());
        if ("HTTPS".equalsIgnoreCase(method)) {
            return cfg.getHttpsEndpoint();
        }
        return (cfg.getSftpHost() != null)
                ? cfg.getSftpHost() + ":" + cfg.getSftpPort() + cfg.getRemoteDir()
                : "unconfigured";
    }

    private void sleepRetryDelay() {
        try {
            Thread.sleep(props.getRetryDelaySeconds() * 1_000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Queries for controller ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SettlementTransmission> listTransmissions(String status) {
        if (status != null && !status.isBlank()) {
            return transmissionRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return transmissionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public SettlementTransmission getTransmission(UUID id) {
        return transmissionRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("TRANSMISSION_NOT_FOUND",
                        "Settlement transmission not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SettlementTransmission> listTransmissionsForBatch(UUID batchId) {
        return transmissionRepository.findByBatchIdOrderByCreatedAtDesc(batchId);
    }
}

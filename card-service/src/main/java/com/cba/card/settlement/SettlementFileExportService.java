package com.cba.card.settlement;

import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
 * Orchestrates the settlement file export.
 *
 * <h3>Issuer or acquirer (backlog 21)</h3>
 * The exporters write acquirer presentments. An issuer receives those and doesn't send them, so unless
 * {@code card.settlement.export.role} is {@code ACQUIRER} nothing is exported: the scheduled jobs return at
 * once and a manual export is refused ({@code 409 PRESENTMENT_REQUIRES_ACQUIRER_ROLE}).
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Nightly ({@code card.settlement.export.export-cron}): CLOSED or SETTLED batches from the last
 *       {@code lookback-days} — a batch that closed late is still picked up — exported under their own
 *       settlement date. It only makes first attempts: a batch+scheme tried before is left alone.</li>
 *   <li>Per scheme, inserting a PENDING {@code settlement_transmissions} row claims the file; the unique index
 *       on live rows (V14) means a concurrent run gets a duplicate key and skips.</li>
 *   <li>An attempt moves the row to SENDING, builds and transmits the file. Success → TRANSMITTED. A transport
 *       failure → back to PENDING with {@code next_retry_at}; the retry poller sends it again, up to
 *       {@code max-retries}, then FAILED. A file that can't be built (bad data) → FAILED at once.</li>
 *   <li>No transaction spans an attempt and nothing sleeps: each status change is its own short write.</li>
 *   <li>A row left SENDING past {@code sending-lease-minutes} (the process died mid-send) becomes FAILED and
 *       is never resent automatically — the file may have arrived. A manual export sends it again.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementFileExportService {

    static final String PENDING     = "PENDING";
    static final String SENDING     = "SENDING";
    static final String TRANSMITTED = "TRANSMITTED";
    static final String FAILED      = "FAILED";

    private static final List<SettlementBatchStatus> EXPORTABLE =
            List.of(SettlementBatchStatus.CLOSED, SettlementBatchStatus.SETTLED);

    private final SettlementBatchRepository          batchRepository;
    private final SettlementTransmissionRepository   transmissionRepository;
    private final List<SettlementFileExporter>       exporters;
    private final SettlementFileTransmitter          transmitter;
    private final SettlementExportProperties         props;
    private final JdbcTemplate                       jdbc;

    // ── Scheduled jobs ────────────────────────────────────────────────────────

    @Scheduled(cron = "${card.settlement.export.export-cron:0 58 23 * * *}")
    public void runNightlyExport() {
        if (!props.isAcquirer()) {
            log.info("Settlement file export skipped: card.settlement.export.role is {} — an issuer sends no "
                    + "presentment files", props.getRole());
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(Math.max(props.getLookbackDays(), 0));
        List<SettlementBatch> batches = batchRepository
                .findByStatusInAndSettlementDateBetweenOrderBySettlementDateAsc(EXPORTABLE, from, today);
        log.info("Settlement file export: {} closed batch(es) since {}", batches.size(), from);

        for (SettlementBatch batch : batches) {
            try {
                export(batch, false);
            } catch (Exception e) {
                log.error("Batch export failed: batchId={} error={}", batch.getId(), e.getMessage());
            }
        }
    }

    /** Sends transmissions whose retry is due, and closes sends that were interrupted. */
    @Scheduled(fixedDelayString = "${card.settlement.export.retry-poll-ms:60000}")
    public void retryDueTransmissions() {
        if (!props.isAcquirer()) return;
        OffsetDateTime now = OffsetDateTime.now();
        failInterruptedSends(now);
        for (SettlementTransmission tx : transmissionRepository
                .findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(PENDING, now)) {
            try {
                retry(tx);
            } catch (Exception e) {
                log.error("Settlement transmission retry failed: id={} error={}", tx.getId(), e.getMessage());
            }
        }
    }

    // ── Manual export ─────────────────────────────────────────────────────────

    /**
     * Export a closed batch now (the REST trigger). Sends each enabled scheme that has no live transmission
     * for the batch — including one whose earlier transmission FAILED — and returns what each first attempt
     * did: TRANSMITTED, or PENDING with {@code nextRetryAt} when the scheme will be tried again.
     *
     * @param settlementDate optional; must be the batch's own settlement date, which the files carry
     */
    public List<SettlementTransmission> exportBatch(UUID batchId, LocalDate settlementDate) {
        if (!props.isAcquirer()) {
            throw CbaException.conflict("PRESENTMENT_REQUIRES_ACQUIRER_ROLE",
                    "Settlement files are acquirer presentments; card.settlement.export.role is "
                            + props.getRole() + ", and an issuer doesn't send them");
        }
        SettlementBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> CbaException.notFound("BATCH_NOT_FOUND",
                        "Settlement batch not found: " + batchId));
        if (!EXPORTABLE.contains(batch.getStatus())) {
            throw CbaException.conflict("BATCH_NOT_CLOSED", "Settlement batch " + batch.getBatchRef() + " is "
                    + batch.getStatus() + ": only a CLOSED or SETTLED batch can be exported");
        }
        if (settlementDate != null && !settlementDate.equals(batch.getSettlementDate())) {
            throw CbaException.badRequest("SETTLEMENT_DATE_MISMATCH", "Settlement batch " + batch.getBatchRef()
                    + " settles on " + batch.getSettlementDate() + ", not " + settlementDate);
        }
        return export(batch, true);
    }

    // ── Export and attempts ───────────────────────────────────────────────────

    private List<SettlementTransmission> export(SettlementBatch batch, boolean manual) {
        LocalDate date = batch.getSettlementDate();
        Map<String, List<SettlementExportRecord>> byScheme = buildExportRecords(batch, date).stream()
                .collect(Collectors.groupingBy(SettlementExportRecord::scheme));

        List<SettlementTransmission> results = new ArrayList<>();
        for (SettlementFileExporter exporter : exporters) {
            String scheme = exporter.getScheme();
            if (!exporter.isEnabled()) {
                log.info("Exporter {} is disabled — skipping", scheme);
                continue;
            }
            List<SettlementExportRecord> records = byScheme.getOrDefault(scheme, List.of());
            if (records.isEmpty()) {
                log.info("No records for scheme={} in batch={} — skipping", scheme, batch.getId());
                continue;
            }
            if (!manual && transmissionRepository.existsByBatchIdAndScheme(batch.getId(), scheme)) {
                log.debug("Batch {} scheme={} was attempted before — left to the retry poller or ops",
                        batch.getId(), scheme);
                continue;
            }
            SettlementTransmission claimed = claim(batch, exporter, records, date);
            if (claimed == null) {
                log.info("Batch {} scheme={} is already transmitted or being sent — skipping", batch.getId(), scheme);
                continue;
            }
            results.add(attempt(claimed, exporter, records, batch));
        }
        return results;
    }

    /** Inserts the PENDING row; null when a live transmission for the batch+scheme already exists. */
    private SettlementTransmission claim(SettlementBatch batch, SettlementFileExporter exporter,
                                         List<SettlementExportRecord> records, LocalDate date) {
        SettlementTransmission tx = new SettlementTransmission();
        tx.setBatchId(batch.getId());
        tx.setScheme(exporter.getScheme());
        tx.setFileName(exporter.generateFileName(batch, date));
        tx.setRecordCount(records.size());
        tx.setTotalAmount(total(records));
        tx.setSettlementDate(date);
        tx.setTransmissionMethod(exporter.transmissionMethod());
        tx.setEndpoint(resolveEndpoint(exporter.getScheme(), exporter.transmissionMethod()));
        try {
            return transmissionRepository.saveAndFlush(tx);
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    /** One attempt: SENDING → TRANSMITTED, back to PENDING (retry due later) or FAILED. */
    private SettlementTransmission attempt(SettlementTransmission tx, SettlementFileExporter exporter,
                                           List<SettlementExportRecord> records, SettlementBatch batch) {
        tx.setStatus(SENDING);
        tx.setAttemptCount(tx.getAttemptCount() + 1);
        tx.setLastAttemptAt(OffsetDateTime.now());
        tx.setNextRetryAt(null);
        tx.setRecordCount(records.size());
        tx.setTotalAmount(total(records));
        try {
            tx = transmissionRepository.saveAndFlush(tx);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Transmission {} was taken by another run — skipping", tx.getId());
            return transmissionRepository.findById(tx.getId()).orElse(tx);
        }

        byte[] file;
        try {
            file = exporter.export(records, batch, tx.getSettlementDate());
        } catch (IllegalArgumentException e) {
            // Bad data (a fractional minor amount, no currency): every retry would build the same file.
            return fail(tx, "Not retried — the file can't be built: " + e.getMessage());
        }

        try {
            log.info("Sending scheme={} file={} attempt={}/{} records={}", exporter.getScheme(), tx.getFileName(),
                    tx.getAttemptCount(), props.getMaxRetries(), records.size());
            transmitter.transmit(file, tx.getFileName(), exporter.getScheme(), exporter.transmissionMethod());
            tx.setStatus(TRANSMITTED);
            tx.setTransmittedAt(OffsetDateTime.now());
            tx.setErrorMessage(null);
            log.info("Settlement file transmitted: scheme={} file={} records={} totalMinor={}",
                    exporter.getScheme(), tx.getFileName(), records.size(), tx.getTotalAmount());
        } catch (Exception e) {
            if (tx.getAttemptCount() >= props.getMaxRetries()) {
                tx.setStatus(FAILED);
                tx.setErrorMessage("All " + tx.getAttemptCount() + " attempts failed. Last: " + e.getMessage());
                log.error("Settlement export FAILED after {} attempts: scheme={} batch={}",
                        tx.getAttemptCount(), exporter.getScheme(), tx.getBatchId());
            } else {
                tx.setStatus(PENDING);
                tx.setNextRetryAt(OffsetDateTime.now().plusSeconds(props.getRetryDelaySeconds()));
                tx.setErrorMessage(e.getMessage());
                log.warn("Transmission attempt {}/{} failed for scheme={}: {} — retrying at {}", tx.getAttemptCount(),
                        props.getMaxRetries(), exporter.getScheme(), e.getMessage(), tx.getNextRetryAt());
            }
        }
        return transmissionRepository.save(tx);
    }

    private void retry(SettlementTransmission tx) {
        String scheme = tx.getScheme();
        SettlementFileExporter exporter = exporters.stream()
                .filter(e -> e.getScheme().equals(scheme)).findFirst().orElse(null);
        SettlementBatch batch = batchRepository.findById(tx.getBatchId()).orElse(null);
        if (exporter == null || !exporter.isEnabled() || batch == null) {
            fail(tx, "Not retried — the " + scheme + " exporter is disabled or the batch is gone");
            return;
        }
        List<SettlementExportRecord> records = buildExportRecords(batch, tx.getSettlementDate()).stream()
                .filter(r -> scheme.equals(r.scheme())).toList();
        if (records.isEmpty()) {
            fail(tx, "Not retried — batch " + batch.getBatchRef() + " has no " + scheme + " items left");
            return;
        }
        attempt(tx, exporter, records, batch);
    }

    /** SENDING rows past their lease: the process died mid-send, so the file may or may not have arrived. */
    void failInterruptedSends(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusMinutes(props.getSendingLeaseMinutes());
        for (SettlementTransmission tx : transmissionRepository.findByStatusAndLastAttemptAtBefore(SENDING, cutoff)) {
            fail(tx, "INTERRUPTED: the attempt started at " + tx.getLastAttemptAt() + " never finished — confirm "
                    + "with the scheme whether " + tx.getFileName() + " arrived before re-exporting");
        }
    }

    private SettlementTransmission fail(SettlementTransmission tx, String reason) {
        tx.setStatus(FAILED);
        tx.setNextRetryAt(null);
        tx.setErrorMessage(reason);
        log.error("Settlement transmission {} FAILED: {}", tx.getId(), reason);
        try {
            return transmissionRepository.save(tx);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Transmission {} changed meanwhile — left as it is", tx.getId());
            return transmissionRepository.findById(tx.getId()).orElse(tx);
        }
    }

    /** Sum of gross amounts, in minor units. */
    private static BigDecimal total(List<SettlementExportRecord> records) {
        return BigDecimal.valueOf(SettlementAmounts.totalMinor(records));
    }

    // ── Record builder ────────────────────────────────────────────────────────

    /**
     * Build normalized {@link SettlementExportRecord} list for a batch: its cleared (SETTLED) items, joined with
     * authorization_log and cards. Amounts are the item's own figures in DE4 minor units — gross, and since
     * backlog 15b the interchange, scheme fee and net (gl_net) the backend posted. (interchange_log isn't read:
     * its rows are minor units before V13 and major units after.)
     *
     * <p>Package-private (not {@code private}) so the Testcontainers integration
     * test can validate the SQL — scheme BIN range-scan, {@code UNION_PAY→UNIONPAY}
     * normalization, masked PAN and the item's figures — against a real PostgreSQL.
     */
    List<SettlementExportRecord> buildExportRecords(
            SettlementBatch batch, LocalDate settlementDate) {

        // Scheme: the item's own (set at approval since V13); before that, a BIN range-scan against the card's
        // 8-digit prefix, normalized so it matches the exporter codes (bin_ranges may hold 'UNION_PAY').
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
                    COALESCE(al.terminal_id, '') AS terminal_id,
                    COALESCE(si.scheme, (
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
                    CASE WHEN al.response_code = '00' THEN COALESCE(al.auth_code, '') ELSE '' END AS auth_code,
                    al.created_at::date    AS transaction_date,
                    COALESCE(si.interchange_amount, 0) AS interchange_amount,
                    COALESCE(si.scheme_fee_amount, 0)  AS scheme_fee_amount,
                    COALESCE(si.gl_net, si.amount)     AS net_amount
                FROM settlement_items si
                LEFT JOIN authorization_log al ON al.id = si.authorization_log_id
                LEFT JOIN cards c            ON c.id  = al.card_id
                WHERE si.batch_id = ?
                  AND si.status = 'SETTLED'
                ORDER BY si.created_at, si.id
                """;

        return jdbc.query(sql, (rs, rowNum) -> new SettlementExportRecord(
                UUID.fromString(rs.getString("settlement_item_id")),
                rs.getString("authorization_log_id") != null
                        ? UUID.fromString(rs.getString("authorization_log_id")) : null,
                rs.getString("masked_pan"),
                "",                                             // pan — masked-only; full PAN decrypt deferred (backlog 2)
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

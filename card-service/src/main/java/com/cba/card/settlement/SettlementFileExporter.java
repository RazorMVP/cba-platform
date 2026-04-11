package com.cba.card.settlement;

import java.time.LocalDate;
import java.util.List;

/**
 * Contract for a scheme-specific clearinghouse file serializer.
 *
 * <h3>Implementation contract</h3>
 * <ul>
 *   <li>One implementation per card scheme (Visa, Mastercard, Verve, Afrigo, UnionPay).</li>
 *   <li>{@link #export} must be deterministic — same inputs always produce the same bytes.
 *       This ensures safe retry on transmission failure.</li>
 *   <li>{@link #export} must NOT perform any I/O or DB calls — it is a pure serializer.
 *       All data is provided in {@code records}.</li>
 *   <li>{@link #isEnabled} guards against misconfigured environments. A disabled exporter
 *       is silently skipped — no error, no transmission attempt.</li>
 * </ul>
 *
 * <h3>Production implementation guide</h3>
 * When the scheme spec document is available:
 * <ol>
 *   <li>Replace the stub body in {@link #export} with the actual byte-level serializer.</li>
 *   <li>Implement {@link #generateFileName} per the scheme's naming convention
 *       (typically: member BIN + date + sequence number).</li>
 *   <li>Configure credentials and endpoint in {@code application.yml} under
 *       {@code card.settlement.schemes.{schemeLower}}.</li>
 *   <li>Set {@code enabled: true} in config — the orchestrator will start sending.</li>
 * </ol>
 * Zero changes to {@link SettlementFileExportService}, {@link SettlementFileTransmitter},
 * or any other infrastructure code are required.
 */
public interface SettlementFileExporter {

    /**
     * The card scheme this exporter handles.
     * Used by {@link SettlementFileExportService} to route items.
     */
    String getScheme();

    /**
     * Whether this exporter is enabled and has valid credentials configured.
     * Returns {@code false} if the scheme endpoint or credentials are absent.
     * A disabled exporter is skipped with an INFO log — not an error.
     */
    boolean isEnabled();

    /**
     * Serialize the provided cleared transactions into the scheme's
     * proprietary clearinghouse file format.
     *
     * <p>This is a pure function — no I/O, no DB access, no side effects.
     * The caller guarantees all records belong to the same scheme and batch.
     *
     * @param records      cleared settlement records for this scheme and batch
     * @param batch        the settlement batch being exported (for header/trailer data)
     * @param exportDate   the settlement date (used in file headers and filename)
     * @return raw file bytes ready for transmission; never null, never empty
     */
    byte[] export(List<SettlementExportRecord> records,
                  SettlementBatch batch,
                  LocalDate exportDate);

    /**
     * Generate the filename expected by the scheme clearinghouse.
     *
     * <p>Each scheme mandates a specific naming pattern, typically including:
     * the issuer/acquirer BIN, settlement date, and a sequence counter.
     * Example Visa BASE II name: {@code V0100123456789012345678901234567} (31 chars).
     *
     * @param batch      the batch being exported
     * @param exportDate the settlement date
     * @return filename string — must conform exactly to scheme naming rules
     */
    String generateFileName(SettlementBatch batch, LocalDate exportDate);

    /**
     * The transmission method preferred by this scheme's clearinghouse.
     * Defaults to SFTP. Override to return "HTTPS" for REST-based clearinghouses.
     */
    default String transmissionMethod() {
        return "SFTP";
    }
}

package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Afrigo / PAPSS (Pan-African Payment and Settlement System) clearinghouse exporter.
 *
 * <h3>Stub status</h3>
 * Replace {@link #export} with the real PAPSS clearing serializer once the PAPSS
 * participant technical specification is available from the Afreximbank / PAPSS team.
 *
 * <h3>PAPSS format overview</h3>
 * <ul>
 *   <li>PAPSS is operated by Afreximbank for intra-African cross-border payments</li>
 *   <li>Unlike Visa/MC, PAPSS is REST-API based — files are JSON payloads, not binary records</li>
 *   <li>Transmission: HTTPS POST to PAPSS gateway ({@code gateway.papss.com})</li>
 *   <li>Authentication: OAuth 2.0 client credentials + mutual TLS</li>
 *   <li>Multi-currency: supports all African Union currencies natively</li>
 *   <li>Settlement: T+1 via Afreximbank correspondent accounts</li>
 * </ul>
 *
 * <h3>Expected JSON payload structure (from PAPSS public documentation)</h3>
 * <pre>
 * {
 *   "participantId": "CBA001",
 *   "settlementDate": "2026-11-30",
 *   "batchReference": "...",
 *   "transactions": [
 *     {
 *       "transactionId": "...",
 *       "rrn": "...",
 *       "pan": "...",
 *       "amount": ...,
 *       "currency": "...",
 *       "merchantId": "...",
 *       "authCode": "...",
 *       "transactionDate": "..."
 *     }
 *   ]
 * }
 * </pre>
 *
 * <h3>To complete this implementation</h3>
 * <ol>
 *   <li>Obtain PAPSS participant technical specification from Afreximbank</li>
 *   <li>Replace export() body with Jackson JSON serialization of the payload</li>
 *   <li>Override {@link #transmissionMethod()} to return "HTTPS" (already done below)</li>
 *   <li>Set {@code card.settlement.export.schemes.afrigo.enabled=true}</li>
 *   <li>Configure {@code https-endpoint} and {@code https-api-key} in application.yml</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfrigoPapssExporter implements SettlementFileExporter {

    private final SettlementExportProperties props;

    @Override
    public String getScheme() { return "AFRIGO"; }

    @Override
    public boolean isEnabled() {
        return props.forScheme("afrigo").isEnabled();
    }

    /** PAPSS uses REST/HTTPS — not SFTP. */
    @Override
    public String transmissionMethod() { return "HTTPS"; }

    /**
     * PAPSS batch reference filename (used as the JSON payload filename in the
     * transmission log). Format: PARTICIPANT_ID + YYYYMMDD + .json
     */
    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        String participantId = resolveParticipantId();
        String date = exportDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return participantId + "_" + date + "_settlement.json";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("[STUB] AfrigoPapssExporter.export() called: {} records for batch={} date={}",
                records.size(), batch.getBatchRef(), exportDate);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_stub\": \"AFRIGO/PAPSS STUB PAYLOAD — NOT FOR PRODUCTION\",\n");
        sb.append("  \"participantId\": \"").append(resolveParticipantId()).append("\",\n");
        sb.append("  \"settlementDate\": \"").append(exportDate).append("\",\n");
        sb.append("  \"batchReference\": \"").append(batch.getBatchRef()).append("\",\n");
        sb.append("  \"recordCount\": ").append(records.size()).append(",\n");
        sb.append("  \"_fieldLayout\": \"Replace this body with real PAPSS JSON schema per Afreximbank spec\",\n");
        sb.append("  \"transactions\": [\n");
        for (int i = 0; i < records.size(); i++) {
            SettlementExportRecord r = records.get(i);
            sb.append("    {");
            sb.append("\"rrn\":\"").append(r.rrn()).append("\"");
            sb.append(",\"pan\":\"").append(r.maskedPan()).append("\"");
            sb.append(",\"amount\":").append(r.grossAmount());
            sb.append(",\"currency\":\"").append(r.currencyCode()).append("\"");
            sb.append(",\"merchantId\":\"").append(r.merchantId()).append("\"");
            sb.append(",\"authCode\":\"").append(r.authCode()).append("\"");
            sb.append(",\"transactionDate\":\"").append(r.transactionDate()).append("\"");
            sb.append("}");
            if (i < records.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String resolveParticipantId() {
        String pid = props.forScheme("afrigo").getParticipantId();
        return (pid != null && !pid.isBlank()) ? pid : props.getMemberId();
    }
}

package com.cba.card.settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * China UnionPay CUPS (China UnionPay Settlement) / CNAPS clearinghouse file exporter.
 *
 * <h3>Stub status</h3>
 * Replace {@link #export} with the real CUPS serializer once the UnionPay International
 * (UPI) member technical specification is available from your CUP relationship manager.
 *
 * <h3>CUPS format overview</h3>
 * <ul>
 *   <li>CUPS is the domestic Chinese card settlement system operated by UnionPay</li>
 *   <li>For international (non-mainland) transactions: UPI International Settlement</li>
 *   <li>File format: fixed-width binary records (similar to Visa BASE II structure)</li>
 *   <li>Character encoding: GB18030 (Chinese national standard, superset of GBK/GB2312)</li>
 *   <li>Transmission: SFTP to CUP settlement server ({@code cups.unionpay.com})</li>
 *   <li>Domestic transactions may also route through CNAPS (central bank RTGS)</li>
 *   <li>SM4 cryptography used for file integrity checking (see ArqcValidator SM4 path)</li>
 * </ul>
 *
 * <h3>Key CUPS fields (from public UPI documentation)</h3>
 * <pre>
 *   Field               Description
 *   MemberID            CUP-assigned member institution ID
 *   SystemTraceNo       DE11 STAN
 *   TransmissionDateTime DE7
 *   PAN                 Primary Account Number
 *   ProcessingCode      DE3
 *   TransactionAmount   DE4 in minor units
 *   CurrencyCode        DE49 ISO 4217 numeric
 *   AcquirerID          DE32
 *   ForwardingInstID    DE33
 *   MerchantType        DE18 MCC
 *   TerminalID          DE41
 *   CardAcceptorID      DE42
 *   ResponseCode        DE39
 *   AuthIDResponse      DE38
 *   PosEntryMode        DE22
 * </pre>
 *
 * <h3>Note on domestic vs international</h3>
 * Domestic China transactions (issued in mainland China, acquired in mainland China)
 * route through CNAPS via the People's Bank of China. International transactions
 * (cross-border or non-mainland) route through CUP International Settlement.
 * The file format differs between these two paths — confirm with your CUP liaison
 * which path applies to your institution's membership type.
 *
 * <h3>To complete this implementation</h3>
 * <ol>
 *   <li>Obtain UPI International Settlement specification from CUP relationship manager</li>
 *   <li>Implement fixed-width record serializer using GB18030 charset where needed</li>
 *   <li>Set {@code card.settlement.export.schemes.unionpay.enabled=true}</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnionPayCupsExporter implements SettlementFileExporter {

    private final SettlementExportProperties props;

    @Override
    public String getScheme() { return "UNIONPAY"; }

    @Override
    public boolean isEnabled() {
        return props.forScheme("unionpay").isEnabled();
    }

    @Override
    public String transmissionMethod() { return "SFTP"; }

    /**
     * CUPS filename: member-id + YYYYMMDD + .cup
     * Example: CBA00120261130.cup
     */
    @Override
    public String generateFileName(SettlementBatch batch, LocalDate exportDate) {
        String participantId = resolveParticipantId();
        String date = exportDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return participantId + date + ".cup";
    }

    @Override
    public byte[] export(List<SettlementExportRecord> records,
                         SettlementBatch batch,
                         LocalDate exportDate) {
        log.info("[STUB] UnionPayCupsExporter.export() called: {} records for batch={} date={}",
                records.size(), batch.getBatchRef(), exportDate);

        StringBuilder sb = new StringBuilder();
        sb.append("=== UNIONPAY CUPS STUB FILE — NOT FOR PRODUCTION ===\n");
        sb.append("Batch: ").append(batch.getBatchRef()).append("\n");
        sb.append("Settlement Date: ").append(exportDate).append("\n");
        sb.append("Record Count: ").append(records.size()).append("\n\n");
        sb.append("FORMAT: Fixed-width binary (GB18030 encoding for Chinese chars)\n");
        sb.append("NOTE: Confirm domestic CNAPS vs international UPI path with CUP liaison\n\n");
        sb.append("EXPECTED FIELDS (per UPI spec):\n");
        sb.append("  MemberID | SystemTraceNo | TransmissionDateTime | PAN\n");
        sb.append("  | ProcessingCode | TransactionAmount | CurrencyCode\n");
        sb.append("  | AcquirerID | MerchantType | TerminalID | CardAcceptorID\n");
        sb.append("  | ResponseCode | AuthIDResponse | PosEntryMode\n\n");
        sb.append("DATA RECORDS:\n");
        for (SettlementExportRecord r : records) {
            sb.append(String.format("  pan=%s stan=%s rrn=%s auth=%s amount=%s ccy=%s mcc=%s%n",
                    r.maskedPan(), r.stan(), r.rrn(), r.authCode(),
                    r.grossAmount(), r.currencyCode(), r.mcc()));
        }
        sb.append("\n=== END OF STUB FILE ===\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String resolveParticipantId() {
        String pid = props.forScheme("unionpay").getParticipantId();
        return (pid != null && !pid.isBlank()) ? pid : props.getMemberId();
    }
}

package com.cba.card.settlement;

import com.cba.card.auth.CardAuthorizationService;
import com.cba.card.interchange.Schemes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;

/**
 * Records card schemes' settlement reports (the cash leg, backlog 15b, decision 4a). For one scheme,
 * settlement date and currency, the expected net is what the bank owes the scheme: the {@code glNet} of
 * that day's cleared items plus the {@code refundGlNet} of that day's refunds. Operations enter the
 * scheme's reported net; only an exact match is recorded — the backend then posts DR 2300 / CR 1003
 * (or the reverse) and the day's batches become SETTLED. A mismatch is refused with the difference and
 * writes nothing; there is no tolerance (backlog 15d).
 *
 * <p>The date must be over (before today): refunds are dated the day they happen and new items join
 * today's batch, so the set of items for an earlier date can't change once its batches are closed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemeSettlementService {

    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

    private final SchemeSettlementReportRepository reportRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementBatchRepository batchRepository;
    private final BackendClearingClient clearingClient;
    private final PlatformTransactionManager transactionManager;

    /** The items and refunds a report would settle, and their net in major units. */
    private record Expected(List<SettlementItem> items, List<SettlementItem> refunds, BigDecimal net) {}

    /** What has to be posted after the first transaction: the report row's figures. */
    private record Pending(java.util.UUID reportId, String reference, BigDecimal amount, SchemeReportResult early) {}

    /** The expected net for a scheme, date and currency (preview; nothing written). */
    @Transactional(readOnly = true)
    public SchemeReportResult expected(String scheme, LocalDate date, String currency) {
        String s = Schemes.normalize(scheme);
        Currency ccy = CardAuthorizationService.currency(currency);
        if (ccy == null) return refused("UNKNOWN_CURRENCY", s, date, currency, null, null, null);
        Expected e = expectedFor(s, date, ccy);
        return new SchemeReportResult(false, false, null, s, date, ccy.getCurrencyCode(), e.net(), null, null,
                e.items().size(), e.refunds().size(), null, null);
    }

    @Transactional(readOnly = true)
    public List<SchemeSettlementReport> list(LocalDate date) {
        return date == null ? reportRepository.findAll()
                : reportRepository.findBySettlementDateOrderBySchemeAscCurrencyCodeAsc(date);
    }

    /**
     * Records a scheme's settlement report: refused unless it equals the expected net; on a match the cash
     * leg posts on the backend (idempotent) and the date's batches become SETTLED when fully reported.
     */
    public SchemeReportResult record(SchemeReportRequest request, String actor) {
        String scheme = Schemes.normalize(request.scheme());
        LocalDate date = request.settlementDate();
        Currency ccy = CardAuthorizationService.currency(request.currency());
        BigDecimal reported = request.netAmount();
        if (ccy == null) return refused("UNKNOWN_CURRENCY", scheme, date, request.currency(), null, reported, null);
        String currency = ccy.getCurrencyCode();
        if (!date.isBefore(LocalDate.now())) return refused("DATE_NOT_CLOSED", scheme, date, currency, null, reported, null);
        if (reported.stripTrailingZeros().scale() > Math.max(ccy.getDefaultFractionDigits(), 0)) {
            return refused("INVALID_AMOUNT", scheme, date, currency, null, reported, null);
        }

        Pending pending;
        try {
            pending = new TransactionTemplate(transactionManager).execute(tx -> prepare(scheme, date, ccy, reported, actor));
        } catch (DataIntegrityViolationException e) {
            // Another request inserted the same report at the same moment.
            return refused("REPORT_IN_PROGRESS", scheme, date, currency, null, reported, null);
        }
        if (pending.early() != null) return pending.early();

        // The backend call runs with no transaction open; the report stays POSTING until it answers.
        BackendClearingClient.SettlementPost post = clearingClient.recordSchemeSettlement(
                pending.reference(), scheme, date, currency, pending.amount());
        return new TransactionTemplate(transactionManager).execute(tx -> finish(pending, post));
    }

    /** First transaction: decide, and on a match write the report (POSTING) and stamp its items. */
    private Pending prepare(String scheme, LocalDate date, Currency ccy, BigDecimal reported, String actor) {
        String currency = ccy.getCurrencyCode();
        var existing = reportRepository.findForUpdate(scheme, date, currency).orElse(null);
        if (existing != null) {
            boolean same = existing.getReportedNet().compareTo(reported) == 0;
            if (SchemeSettlementReport.RECORDED.equals(existing.getStatus())) {
                return early(new SchemeReportResult(same, same, same ? null : "ALREADY_RECORDED", scheme, date, currency,
                        existing.getExpectedNet(), reported, reported.subtract(existing.getExpectedNet()),
                        existing.getItemCount(), existing.getRefundCount(), existing.getBackendReference(),
                        existing.getJournalTransactionId()));
            }
            // POSTING: an earlier attempt lost the backend's answer — finish it with the same figures.
            if (!same) return early(refused("REPORT_IN_PROGRESS", scheme, date, currency, existing.getExpectedNet(), reported, null));
            return new Pending(existing.getId(), existing.getBackendReference(), existing.getReportedNet(), null);
        }

        boolean openBatch = batchRepository.findBySettlementDateOrderByOpenedAtDesc(date).stream()
                .anyMatch(b -> b.getStatus() == SettlementBatchStatus.OPEN);
        if (openBatch || itemRepository.countByDateAndStatusIn(date, List.of("PENDING", SettlementService.CLEARING)) > 0) {
            return early(refused("BATCH_NOT_CLOSED", scheme, date, currency, null, reported, null));
        }
        Expected e = expectedFor(scheme, date, ccy);
        if (e.net().compareTo(reported) != 0) {
            log.warn("Scheme report {} {} {} refused: reported {} ≠ expected {}", scheme, date, currency, reported, e.net());
            return early(new SchemeReportResult(false, false, "AMOUNT_MISMATCH", scheme, date, currency, e.net(), reported,
                    reported.subtract(e.net()), e.items().size(), e.refunds().size(), null, null));
        }

        SchemeSettlementReport report = new SchemeSettlementReport();
        report.setScheme(scheme);
        report.setSettlementDate(date);
        report.setCurrencyCode(currency);
        report.setReportedNet(reported);
        report.setExpectedNet(e.net());
        report.setItemCount(e.items().size());
        report.setRefundCount(e.refunds().size());
        report.setBackendReference("CSR-" + scheme + "-" + date.format(BASIC) + "-" + currency);
        report.setCreatedBy(actor);
        // Nothing to move: recorded without a journal.
        boolean nothingToPost = e.net().signum() == 0;
        report.setStatus(nothingToPost ? SchemeSettlementReport.RECORDED : SchemeSettlementReport.POSTING);
        if (nothingToPost) report.setRecordedAt(OffsetDateTime.now());
        SchemeSettlementReport saved = reportRepository.saveAndFlush(report);
        e.items().forEach(i -> i.setSettlementReportId(saved.getId()));
        e.refunds().forEach(i -> i.setRefundReportId(saved.getId()));
        itemRepository.saveAll(e.items());
        itemRepository.saveAll(e.refunds());
        if (nothingToPost) {
            markBatchesSettled(date);
            return early(new SchemeReportResult(true, false, null, scheme, date, currency, e.net(), reported,
                    BigDecimal.ZERO, e.items().size(), e.refunds().size(), saved.getBackendReference(), null));
        }
        return new Pending(saved.getId(), saved.getBackendReference(), reported, null);
    }

    /** Second transaction: the backend's answer. */
    private SchemeReportResult finish(Pending pending, BackendClearingClient.SettlementPost post) {
        SchemeSettlementReport report = reportRepository.findById(pending.reportId()).orElseThrow();
        if (post == null) {
            // Unreachable: the backend may have posted; stay POSTING — the same report again finishes it.
            return result(report, false, false, "BACKEND_UNAVAILABLE");
        }
        if (!post.approved()) {
            // Refused (nothing posted): undo the report so operations can correct and record again.
            itemRepository.findBySettlementReportId(report.getId()).forEach(i -> i.setSettlementReportId(null));
            itemRepository.findByRefundReportId(report.getId()).forEach(i -> i.setRefundReportId(null));
            itemRepository.flush();
            reportRepository.delete(report);
            log.warn("Scheme report {} refused by the backend: {}", report.getBackendReference(), post.reason());
            return result(report, false, false, post.reason());
        }
        report.setStatus(SchemeSettlementReport.RECORDED);
        report.setJournalTransactionId(post.journalTransactionId());
        report.setRecordedAt(OffsetDateTime.now());
        reportRepository.save(report);
        markBatchesSettled(report.getSettlementDate());
        log.info("Scheme report {} recorded: {} {} ({})", report.getBackendReference(), report.getReportedNet(),
                report.getCurrencyCode(), post.journalTransactionId());
        return result(report, true, false, null);
    }

    /** CLOSED batches of the date whose GL items are all in recorded reports become SETTLED. */
    private void markBatchesSettled(LocalDate date) {
        for (SettlementBatch batch : batchRepository.findBySettlementDateOrderByOpenedAtDesc(date)) {
            if (batch.getStatus() == SettlementBatchStatus.CLOSED && itemRepository.countUnsettledGlItems(batch) == 0) {
                batch.setStatus(SettlementBatchStatus.SETTLED);
                batchRepository.save(batch);
                log.info("Settlement batch {} SETTLED", batch.getBatchRef());
            }
        }
    }

    private Expected expectedFor(String scheme, LocalDate date, Currency ccy) {
        List<SettlementItem> items = itemRepository.findUnreportedCleared(scheme, date).stream()
                .filter(i -> ccy.equals(CardAuthorizationService.currency(i.getCurrencyCode()))).toList();
        List<SettlementItem> refunds = itemRepository.findUnreportedRefunds(scheme, date).stream()
                .filter(i -> ccy.equals(CardAuthorizationService.currency(i.getCurrencyCode()))).toList();
        BigDecimal minor = items.stream().map(SettlementItem::getGlNet).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(refunds.stream().map(SettlementItem::getRefundGlNet).reduce(BigDecimal.ZERO, BigDecimal::add));
        int digits = Math.max(ccy.getDefaultFractionDigits(), 0);
        BigDecimal net = CardAuthorizationService.toMajorUnits(minor, ccy).setScale(digits, RoundingMode.HALF_UP);
        return new Expected(items, refunds, net);
    }

    private static Pending early(SchemeReportResult result) {
        return new Pending(null, null, null, result);
    }

    private static SchemeReportResult result(SchemeSettlementReport r, boolean recorded, boolean repeat, String reason) {
        return new SchemeReportResult(recorded, repeat, reason, r.getScheme(), r.getSettlementDate(), r.getCurrencyCode(),
                r.getExpectedNet(), r.getReportedNet(), r.getReportedNet().subtract(r.getExpectedNet()),
                r.getItemCount(), r.getRefundCount(), r.getBackendReference(), r.getJournalTransactionId());
    }

    private static SchemeReportResult refused(String reason, String scheme, LocalDate date, String currency,
                                              BigDecimal expected, BigDecimal reported, BigDecimal difference) {
        return new SchemeReportResult(false, false, reason, scheme, date, currency, expected, reported, difference,
                0, 0, null, null);
    }
}

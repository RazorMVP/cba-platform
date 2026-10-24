package com.cba.card.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SchemeSettlementService} — a scheme's settlement report is recorded only when it equals the
 * expected net of the date's cleared items and refunds (backlog 15b, decision 4a).
 */
@ExtendWith(MockitoExtension.class)
class SchemeSettlementServiceTest {

    static final LocalDate D = LocalDate.now().minusDays(1);

    @Mock SchemeSettlementReportRepository reportRepository;
    @Mock SettlementItemRepository itemRepository;
    @Mock SettlementBatchRepository batchRepository;
    @Mock BackendClearingClient clearingClient;
    @Mock PlatformTransactionManager transactionManager;

    @InjectMocks SchemeSettlementService service;

    private static SettlementItem cleared(String glNetMinor, String currency) {
        SettlementItem i = new SettlementItem();
        i.setId(UUID.randomUUID());
        i.setScheme("VISA");
        i.setCurrencyCode(currency);
        i.setGlNet(new BigDecimal(glNetMinor));
        return i;
    }

    private static SettlementItem refund(String refundGlNetMinor) {
        SettlementItem i = cleared("1000", "840");
        i.setRefundGlNet(new BigDecimal(refundGlNetMinor));
        return i;
    }

    private static SettlementBatch closedBatch() {
        SettlementBatch b = new SettlementBatch();
        b.setId(UUID.randomUUID());
        b.setBatchRef("B-" + UUID.randomUUID());
        b.setSettlementDate(D);
        b.setStatus(SettlementBatchStatus.CLOSED);
        return b;
    }

    private static SchemeReportRequest report(String net) {
        return new SchemeReportRequest("VISA", D, "USD", new BigDecimal(net));
    }

    /** The worked example: purchase 98.63 + ATM 50.57 owed = 149.20; no open batch, nothing clearing. */
    private SettlementBatch stubDay(List<SettlementItem> items, List<SettlementItem> refunds) {
        SettlementBatch batch = closedBatch();
        when(reportRepository.findForUpdate("VISA", D, "USD")).thenReturn(Optional.empty());
        when(batchRepository.findBySettlementDateOrderByOpenedAtDesc(D)).thenReturn(List.of(batch));
        when(itemRepository.countByDateAndStatusIn(eq(D), any())).thenReturn(0L);
        when(itemRepository.findUnreportedCleared("VISA", D)).thenReturn(items);
        when(itemRepository.findUnreportedRefunds("VISA", D)).thenReturn(refunds);
        return batch;
    }

    private void stubSave() {
        when(reportRepository.saveAndFlush(any(SchemeSettlementReport.class))).thenAnswer(i -> {
            SchemeSettlementReport r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    @Test
    @DisplayName("match: posts the cash leg once, stamps the items and settles the date's batch")
    void match_postsAndStampsItems_batchSettled() {
        SettlementItem purchase = cleared("9863", "840");
        SettlementItem atm = cleared("5057", "840");
        SettlementBatch batch = stubDay(List.of(purchase, atm), List.of());
        stubSave();
        when(clearingClient.recordSchemeSettlement(any(), eq("VISA"), eq(D), eq("USD"), any()))
                .thenReturn(new BackendClearingClient.SettlementPost(true, false, "ref", "GL-1", null));
        when(reportRepository.findById(any())).thenAnswer(i -> {
            SchemeSettlementReport r = new SchemeSettlementReport();
            r.setId(i.getArgument(0));
            r.setScheme("VISA"); r.setSettlementDate(D); r.setCurrencyCode("USD");
            r.setReportedNet(new BigDecimal("149.20")); r.setExpectedNet(new BigDecimal("149.20"));
            r.setBackendReference("CSR-VISA-" + D.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-USD");
            r.setStatus(SchemeSettlementReport.POSTING);
            return Optional.of(r);
        });
        when(itemRepository.countUnsettledGlItems(batch)).thenReturn(0L);

        SchemeReportResult r = service.record(report("149.20"), "ops");

        assertThat(r.recorded()).as(r.reason()).isTrue();
        assertThat(r.journalTransactionId()).isEqualTo("GL-1");
        assertThat(purchase.getSettlementReportId()).isNotNull();
        assertThat(atm.getSettlementReportId()).isEqualTo(purchase.getSettlementReportId());
        verify(clearingClient).recordSchemeSettlement(eq("CSR-VISA-" + D.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-USD"),
                eq("VISA"), eq(D), eq("USD"),
                org.mockito.ArgumentMatchers.<BigDecimal>argThat(a -> a.compareTo(new BigDecimal("149.20")) == 0));
        assertThat(batch.getStatus()).isEqualTo(SettlementBatchStatus.SETTLED);
    }

    @Test
    @DisplayName("mismatch: refused with expected, reported and the difference — nothing written, nothing posted")
    void mismatch_writesNothing() {
        stubDay(List.of(cleared("9863", "840"), cleared("5057", "840")), List.of());

        SchemeReportResult r = service.record(report("149.21"), "ops");

        assertThat(r.recorded()).isFalse();
        assertThat(r.reason()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(r.expectedNet()).isEqualByComparingTo("149.20");
        assertThat(r.difference()).isEqualByComparingTo("0.01");
        verify(reportRepository, never()).saveAndFlush(any());
        verify(clearingClient, never()).recordSchemeSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the same report again: repeat, no backend call; another amount after recording: ALREADY_RECORDED")
    void recordedReport_repeatOrRefused() {
        SchemeSettlementReport done = new SchemeSettlementReport();
        done.setScheme("VISA"); done.setSettlementDate(D); done.setCurrencyCode("USD");
        done.setReportedNet(new BigDecimal("149.20")); done.setExpectedNet(new BigDecimal("149.20"));
        done.setStatus(SchemeSettlementReport.RECORDED); done.setJournalTransactionId("GL-1");
        when(reportRepository.findForUpdate("VISA", D, "USD")).thenReturn(Optional.of(done));

        SchemeReportResult again = service.record(report("149.20"), "ops");
        SchemeReportResult other = service.record(report("150.00"), "ops");

        assertThat(again.recorded()).isTrue();
        assertThat(again.repeat()).isTrue();
        assertThat(other.recorded()).isFalse();
        assertThat(other.reason()).isEqualTo("ALREADY_RECORDED");
        verify(clearingClient, never()).recordSchemeSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("refused while the date isn't settled: today, an OPEN batch, items still clearing")
    void notClosed_refused() {
        assertThat(service.record(new SchemeReportRequest("VISA", LocalDate.now(), "USD", BigDecimal.ONE), "ops").reason())
                .isEqualTo("DATE_NOT_CLOSED");

        SettlementBatch open = closedBatch();
        open.setStatus(SettlementBatchStatus.OPEN);
        when(reportRepository.findForUpdate("VISA", D, "USD")).thenReturn(Optional.empty());
        when(batchRepository.findBySettlementDateOrderByOpenedAtDesc(D)).thenReturn(List.of(open));
        assertThat(service.record(report("1.00"), "ops").reason()).isEqualTo("BATCH_NOT_CLOSED");

        when(batchRepository.findBySettlementDateOrderByOpenedAtDesc(D)).thenReturn(List.of(closedBatch()));
        when(itemRepository.countByDateAndStatusIn(eq(D), any())).thenReturn(1L);
        assertThat(service.record(report("1.00"), "ops").reason()).isEqualTo("BATCH_NOT_CLOSED");
        verify(clearingClient, never()).recordSchemeSettlement(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("refused by the backend: the report and the item stamps are undone so operations can retry")
    void backendRefused_rollsBackStamp() {
        SettlementItem purchase = cleared("9863", "840");
        stubDay(List.of(purchase), List.of());
        stubSave();
        when(clearingClient.recordSchemeSettlement(any(), any(), any(), any(), any()))
                .thenReturn(new BackendClearingClient.SettlementPost(false, false, "ref", null, "REFERENCE_CONFLICT"));
        SchemeSettlementReport stored = new SchemeSettlementReport();
        stored.setId(UUID.randomUUID());
        stored.setScheme("VISA"); stored.setSettlementDate(D); stored.setCurrencyCode("USD");
        stored.setReportedNet(new BigDecimal("98.63")); stored.setExpectedNet(new BigDecimal("98.63"));
        when(reportRepository.findById(any())).thenReturn(Optional.of(stored));
        when(itemRepository.findBySettlementReportId(stored.getId())).thenReturn(List.of(purchase));
        when(itemRepository.findByRefundReportId(stored.getId())).thenReturn(List.of());

        SchemeReportResult r = service.record(report("98.63"), "ops");

        assertThat(r.recorded()).isFalse();
        assertThat(r.reason()).isEqualTo("REFERENCE_CONFLICT");
        assertThat(purchase.getSettlementReportId()).isNull();
        verify(reportRepository).delete(stored);
    }

    @Test
    @DisplayName("backend unreachable: the report stays POSTING; recording it again finishes it with the same reference")
    void unreachable_leavesPosting_retryCompletes() {
        stubDay(List.of(cleared("9863", "840")), List.of());
        stubSave();
        SchemeSettlementReport stored = new SchemeSettlementReport();
        stored.setId(UUID.randomUUID());
        stored.setScheme("VISA"); stored.setSettlementDate(D); stored.setCurrencyCode("USD");
        stored.setReportedNet(new BigDecimal("98.63")); stored.setExpectedNet(new BigDecimal("98.63"));
        stored.setBackendReference("CSR-REF"); stored.setStatus(SchemeSettlementReport.POSTING);
        when(reportRepository.findById(any())).thenReturn(Optional.of(stored));
        when(clearingClient.recordSchemeSettlement(any(), any(), any(), any(), any()))
                .thenReturn(null)
                .thenReturn(new BackendClearingClient.SettlementPost(true, true, "CSR-REF", "GL-9", null));

        SchemeReportResult first = service.record(report("98.63"), "ops");
        assertThat(first.recorded()).isFalse();
        assertThat(first.reason()).isEqualTo("BACKEND_UNAVAILABLE");
        assertThat(stored.getStatus()).isEqualTo(SchemeSettlementReport.POSTING);

        when(reportRepository.findForUpdate("VISA", D, "USD")).thenReturn(Optional.of(stored));
        SchemeReportResult retry = service.record(report("98.63"), "ops");

        assertThat(retry.recorded()).isTrue();
        assertThat(stored.getStatus()).isEqualTo(SchemeSettlementReport.RECORDED);
        // The first attempt sends the reference it generated; the retry the stored report's — the same key.
        verify(clearingClient, org.mockito.Mockito.times(2)).recordSchemeSettlement(any(), any(), any(), any(), any());
        verify(clearingClient).recordSchemeSettlement(eq("CSR-REF"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("refunds dated that day count; a net the scheme owes the bank is negative")
    void refunds_negativeNet() {
        stubDay(List.of(), List.of(refund("-3945")));
        stubSave();
        when(clearingClient.recordSchemeSettlement(any(), any(), any(), any(), any()))
                .thenReturn(new BackendClearingClient.SettlementPost(true, false, "ref", "GL-2", null));
        SchemeSettlementReport stored = new SchemeSettlementReport();
        stored.setId(UUID.randomUUID());
        stored.setScheme("VISA"); stored.setSettlementDate(D); stored.setCurrencyCode("USD");
        stored.setReportedNet(new BigDecimal("-39.45")); stored.setExpectedNet(new BigDecimal("-39.45"));
        when(reportRepository.findById(any())).thenReturn(Optional.of(stored));

        SchemeReportResult r = service.record(report("-39.45"), "ops");

        assertThat(r.recorded()).as(r.reason()).isTrue();
        assertThat(r.refundCount()).isZero();   // from the stored report
        verify(clearingClient).recordSchemeSettlement(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.<BigDecimal>argThat(a -> a.compareTo(new BigDecimal("-39.45")) == 0));
    }

    @Test
    @DisplayName("nothing to settle (expected 0) is recorded without a backend call; other currencies don't count")
    void zeroNet_otherCurrencyExcluded() {
        SettlementBatch batch = stubDay(new ArrayList<>(List.of(cleared("5000", "566"))), List.of());   // NGN item
        stubSave();
        when(itemRepository.countUnsettledGlItems(batch)).thenReturn(0L);

        SchemeReportResult r = service.record(report("0.00"), "ops");

        assertThat(r.recorded()).as(r.reason()).isTrue();
        assertThat(r.itemCount()).isZero();
        verify(clearingClient, never()).recordSchemeSettlement(any(), any(), any(), any(), any());
        verify(itemRepository, org.mockito.Mockito.times(2)).saveAll(anyList());
    }

    @Test
    @DisplayName("unknown currency and too many decimals are refused up front")
    void badInput_refused() {
        assertThat(service.record(new SchemeReportRequest("VISA", D, "XYZ", BigDecimal.ONE), "ops").reason())
                .isEqualTo("UNKNOWN_CURRENCY");
        assertThat(service.record(report("1.005"), "ops").reason()).isEqualTo("INVALID_AMOUNT");
    }
}

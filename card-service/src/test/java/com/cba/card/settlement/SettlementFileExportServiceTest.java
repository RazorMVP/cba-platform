package com.cba.card.settlement;

import com.cba.card.common.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SettlementFileExportService} (backlog 21): the issuer role exports nothing; only closed batches, under
 * their own date; a failed attempt is retried later by the poller — never by sleeping; a claim that loses the
 * race skips; a send interrupted mid-way is closed, not resent.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementFileExportServiceTest {

    @Mock SettlementBatchRepository batchRepository;
    @Mock SettlementTransmissionRepository transmissionRepository;
    @Mock SettlementFileTransmitter transmitter;
    @Mock JdbcTemplate jdbc;
    @Mock SettlementFileExporter exporter;

    private final SettlementExportProperties props = new SettlementExportProperties();
    private final LocalDate today = LocalDate.now();
    private SettlementFileExportService service;
    private SettlementBatch batch;

    @BeforeEach
    void setUp() {
        props.setRole("ACQUIRER");
        when(exporter.getScheme()).thenReturn("VISA");
        when(exporter.isEnabled()).thenReturn(true);
        when(exporter.transmissionMethod()).thenReturn("SFTP");
        when(exporter.generateFileName(any(), any())).thenAnswer(i -> "V-" + i.getArgument(1));
        when(exporter.export(any(), any(), any())).thenReturn(new byte[]{1});
        when(transmissionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(transmissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        batch = batch(SettlementBatchStatus.CLOSED, today.minusDays(1));
        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

        service = spy(new SettlementFileExportService(batchRepository, transmissionRepository, List.of(exporter),
                transmitter, props, jdbc));
        doReturn(List.of(record("VISA"))).when(service).buildExportRecords(any(), any());
    }

    private static SettlementBatch batch(SettlementBatchStatus status, LocalDate date) {
        SettlementBatch b = new SettlementBatch();
        b.setId(UUID.randomUUID());
        b.setBatchRef("BATCH-" + date);
        b.setStatus(status);
        b.setSettlementDate(date);
        return b;
    }

    private static SettlementExportRecord record(String scheme) {
        BigDecimal gross = new BigDecimal("2500.0000");
        return new SettlementExportRecord(UUID.randomUUID(), UUID.randomUUID(), "411111******1111", "", "DEBIT",
                scheme, "0100", "000001", "RRN1", "AUTH01", "000000", "051", gross, BigDecimal.ZERO,
                BigDecimal.ZERO, gross, "840", "M1", "Shop", "5411", "T1", "412345", null, null);
    }

    private SettlementTransmission pending(int attempts) {
        SettlementTransmission tx = new SettlementTransmission();
        tx.setId(UUID.randomUUID());
        tx.setBatchId(batch.getId());
        tx.setScheme("VISA");
        tx.setFileName("V-" + batch.getSettlementDate());
        tx.setSettlementDate(batch.getSettlementDate());
        tx.setAttemptCount(attempts);
        tx.setNextRetryAt(OffsetDateTime.now().minusSeconds(1));
        return tx;
    }

    // ── Issuer role (the default) ─────────────────────────────────────────────

    @Test
    @DisplayName("ISSUER (the default): nightly and poller send nothing, manual export is refused with 409")
    void issuerRoleExportsNothing() {
        assertThat(new SettlementExportProperties().isAcquirer()).isFalse();
        props.setRole("ISSUER");

        service.runNightlyExport();
        service.retryDueTransmissions();
        assertThatThrownBy(() -> service.exportBatch(batch.getId(), null))
                .isInstanceOfSatisfying(CbaException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("PRESENTMENT_REQUIRES_ACQUIRER_ROLE");
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });

        verifyNoInteractions(batchRepository, transmissionRepository, transmitter);
    }

    @Test
    @DisplayName("an unrecognised role counts as ISSUER")
    void unknownRoleIsIssuer() {
        props.setRole("acquirer-ish");
        assertThat(props.isAcquirer()).isFalse();
        props.setRole(" acquirer ");
        assertThat(props.isAcquirer()).isTrue();
    }

    // ── Manual export ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("an OPEN batch is refused (409) — exporting it would send a partial file")
    void openBatchRefused() {
        SettlementBatch open = batch(SettlementBatchStatus.OPEN, today);
        when(batchRepository.findById(open.getId())).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.exportBatch(open.getId(), null))
                .isInstanceOfSatisfying(CbaException.class, e -> assertThat(e.getCode()).isEqualTo("BATCH_NOT_CLOSED"));
        verify(transmitter, never()).transmit(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a date other than the batch's is refused (400); the files carry the batch's date")
    void dateMismatchRefused() {
        assertThatThrownBy(() -> service.exportBatch(batch.getId(), today))
                .isInstanceOfSatisfying(CbaException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("SETTLEMENT_DATE_MISMATCH");
                    assertThat(e.getStatus().value()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("manual export of yesterday's batch: file named and built for yesterday, TRANSMITTED, total in minor units")
    void manualExportUsesBatchDate() {
        List<SettlementTransmission> result = service.exportBatch(batch.getId(), null);

        assertThat(result).singleElement().satisfies(tx -> {
            assertThat(tx.getStatus()).isEqualTo("TRANSMITTED");
            assertThat(tx.getAttemptCount()).isEqualTo(1);
            assertThat(tx.getSettlementDate()).isEqualTo(today.minusDays(1));
            assertThat(tx.getFileName()).isEqualTo("V-" + today.minusDays(1));
            assertThat(tx.getTotalAmount()).isEqualByComparingTo("2500");
        });
        verify(exporter).export(any(), eq(batch), eq(today.minusDays(1)));
        verify(transmitter).transmit(any(), eq("V-" + today.minusDays(1)), eq("VISA"), eq("SFTP"));
    }

    @Test
    @DisplayName("a failed send goes back to PENDING with a retry time — no sleep, the call returns at once")
    void failedAttemptIsScheduledNotSlept() {
        doThrow(new SettlementTransmissionException("connection refused"))
                .when(transmitter).transmit(any(), anyString(), anyString(), anyString());
        long started = System.nanoTime();

        List<SettlementTransmission> result = service.exportBatch(batch.getId(), null);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        assertThat(result).singleElement().satisfies(tx -> {
            assertThat(tx.getStatus()).isEqualTo("PENDING");
            assertThat(tx.getAttemptCount()).isEqualTo(1);
            assertThat(tx.getErrorMessage()).contains("connection refused");
            assertThat(tx.getNextRetryAt())
                    .isCloseTo(OffsetDateTime.now().plusSeconds(300), within(Duration.ofSeconds(30)));
        });
    }

    @Test
    @DisplayName("a lost claim (duplicate key on the live-row index) skips the scheme — nothing is sent twice")
    void lostClaimSkips() {
        when(transmissionRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(service.exportBatch(batch.getId(), null)).isEmpty();
        verify(transmitter, never()).transmit(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a file that can't be built (bad data) FAILS at once — retrying would build the same file")
    void badDataFailsAtOnce() {
        when(exporter.export(any(), any(), any())).thenThrow(new IllegalArgumentException("has no currency"));

        assertThat(service.exportBatch(batch.getId(), null)).singleElement().satisfies(tx -> {
            assertThat(tx.getStatus()).isEqualTo("FAILED");
            assertThat(tx.getNextRetryAt()).isNull();
            assertThat(tx.getErrorMessage()).contains("can't be built").contains("has no currency");
        });
        verify(transmitter, never()).transmit(any(), anyString(), anyString(), anyString());
    }

    // ── Nightly run ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("nightly: a batch that closed late (yesterday) is exported today under yesterday's date")
    void nightlyPicksUpLateBatch() {
        when(batchRepository.findByStatusInAndSettlementDateBetweenOrderBySettlementDateAsc(
                List.of(SettlementBatchStatus.CLOSED, SettlementBatchStatus.SETTLED), today.minusDays(7), today))
                .thenReturn(List.of(batch));

        service.runNightlyExport();

        verify(exporter).generateFileName(batch, today.minusDays(1));
        verify(transmitter).transmit(any(), eq("V-" + today.minusDays(1)), eq("VISA"), eq("SFTP"));
    }

    @Test
    @DisplayName("nightly: a batch+scheme attempted before is left to the poller or ops — no automatic resend")
    void nightlySkipsAttemptedBefore() {
        when(batchRepository.findByStatusInAndSettlementDateBetweenOrderBySettlementDateAsc(any(), any(), any()))
                .thenReturn(List.of(batch));
        when(transmissionRepository.existsByBatchIdAndScheme(batch.getId(), "VISA")).thenReturn(true);

        service.runNightlyExport();

        verify(transmissionRepository, never()).saveAndFlush(any());
        verify(transmitter, never()).transmit(any(), anyString(), anyString(), anyString());
    }

    // ── Retry poller ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("poller: a due PENDING transmission is sent again and TRANSMITTED")
    void pollerRetriesDueRow() {
        SettlementTransmission due = pending(1);
        when(transmissionRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(due));

        service.retryDueTransmissions();

        assertThat(due.getStatus()).isEqualTo("TRANSMITTED");
        assertThat(due.getAttemptCount()).isEqualTo(2);
        verify(transmitter).transmit(any(), eq(due.getFileName()), eq("VISA"), eq("SFTP"));
    }

    @Test
    @DisplayName("poller: the last allowed attempt failing → FAILED, no further retry")
    void pollerGivesUpAfterMaxRetries() {
        SettlementTransmission due = pending(props.getMaxRetries() - 1);
        when(transmissionRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(due));
        doThrow(new SettlementTransmissionException("timeout"))
                .when(transmitter).transmit(any(), anyString(), anyString(), anyString());

        service.retryDueTransmissions();

        assertThat(due.getStatus()).isEqualTo("FAILED");
        assertThat(due.getNextRetryAt()).isNull();
        assertThat(due.getErrorMessage()).startsWith("All 3 attempts failed").contains("timeout");
    }

    @Test
    @DisplayName("poller: a send stuck in SENDING past its lease → FAILED (INTERRUPTED), never resent")
    void interruptedSendIsClosedNotResent() {
        SettlementTransmission stuck = pending(1);
        stuck.setStatus("SENDING");
        stuck.setNextRetryAt(null);
        stuck.setLastAttemptAt(OffsetDateTime.now().minusMinutes(45));
        when(transmissionRepository.findByStatusAndLastAttemptAtBefore(eq("SENDING"), any()))
                .thenReturn(List.of(stuck));

        service.retryDueTransmissions();

        assertThat(stuck.getStatus()).isEqualTo("FAILED");
        assertThat(stuck.getErrorMessage()).startsWith("INTERRUPTED").contains(stuck.getFileName());
        verify(transmitter, never()).transmit(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("poller: a disabled exporter → FAILED, not retried")
    void pollerFailsWhenExporterDisabled() {
        SettlementTransmission due = pending(1);
        when(transmissionRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(due));
        when(exporter.isEnabled()).thenReturn(false);

        service.retryDueTransmissions();

        assertThat(due.getStatus()).isEqualTo("FAILED");
        verify(transmitter, never()).transmit(any(), anyString(), anyString(), anyString());
    }
}

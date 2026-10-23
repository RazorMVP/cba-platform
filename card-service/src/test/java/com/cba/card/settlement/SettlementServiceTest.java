package com.cba.card.settlement;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.card.Card;
import com.cba.card.card.CardType;
import com.cba.card.common.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for {@link SettlementService} — batch open/add/close, clearing outcomes and expiry. */
@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock SettlementBatchRepository batchRepository;
    @Mock SettlementItemRepository itemRepository;
    @Mock AuthorizationLogRepository authLogRepository;
    @Mock BackendClearingClient clearingClient;
    @Mock PlatformTransactionManager transactionManager;   // TransactionTemplate runs the callback

    @InjectMocks SettlementService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "authExpiryDays", 7);
    }

    private static SettlementBatch openBatch() {
        SettlementBatch b = new SettlementBatch();
        b.setId(UUID.randomUUID());
        b.setBatchRef(UUID.randomUUID().toString());
        b.setSettlementDate(LocalDate.now());
        b.setStatus(SettlementBatchStatus.OPEN);
        return b;
    }

    /** A PENDING-style debit item that {@code findById} returns as unchanged (same instance, same version). */
    private SettlementItem debitItem(String status) {
        SettlementItem i = new SettlementItem();
        i.setId(UUID.randomUUID());
        i.setStatus(status);
        i.setCardType("DEBIT");
        i.setAccountId(UUID.randomUUID());
        i.setHoldReference("CARD-" + UUID.randomUUID());
        i.setAmount(new BigDecimal("2500"));   // DE4 minor units
        i.setCurrencyCode("840");
        return i;
    }

    private void stubBatch(SettlementBatch batch, SettlementItem... items) {
        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatch(batch)).thenReturn(List.of(items));
    }

    private void stubReread(SettlementItem item) {
        when(itemRepository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
    }

    // ── Opening and adding ───────────────────────────────────────────────────

    @Test
    @DisplayName("openOrGetTodaysBatch returns the existing open batch without creating one")
    void returnsExistingOpenBatch() {
        SettlementBatch existing = openBatch();
        when(batchRepository.findBySettlementDateAndStatus(any(LocalDate.class), eq(SettlementBatchStatus.OPEN)))
                .thenReturn(Optional.of(existing));

        assertThat(service.openOrGetTodaysBatch()).isSameAs(existing);
        verify(batchRepository, never()).insertOpenBatchIfAbsent(any());
    }

    @Test
    @DisplayName("openOrGetTodaysBatch creates the day's batch with ON CONFLICT DO NOTHING, then reads it")
    void createsNewBatch() {
        SettlementBatch created = openBatch();
        when(batchRepository.findBySettlementDateAndStatus(any(LocalDate.class), eq(SettlementBatchStatus.OPEN)))
                .thenReturn(Optional.empty(), Optional.of(created));

        assertThat(service.openOrGetTodaysBatch()).isSameAs(created);
        verify(batchRepository).insertOpenBatchIfAbsent(LocalDate.now());
    }

    @Test
    @DisplayName("addToCurrentBatch records a PENDING item and adds it to the totals in one UPDATE")
    void addToCurrentBatch() {
        SettlementBatch batch = openBatch();
        when(batchRepository.findBySettlementDateAndStatus(any(LocalDate.class), eq(SettlementBatchStatus.OPEN)))
                .thenReturn(Optional.of(batch));
        when(itemRepository.save(any(SettlementItem.class))).thenAnswer(i -> i.getArgument(0));

        SettlementItem item = service.addToCurrentBatch(UUID.randomUUID(), new BigDecimal("100.00"), "840");

        assertThat(item.getStatus()).isEqualTo("PENDING");
        verify(batchRepository).addToTotals(batch.getId(), new BigDecimal("100.00"));
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("addAuthorization: a debit purchase is PENDING with its account + hold; a completed one SETTLED")
    void addAuthorization() {
        SettlementBatch batch = openBatch();
        when(batchRepository.findBySettlementDateAndStatus(any(LocalDate.class), eq(SettlementBatchStatus.OPEN)))
                .thenReturn(Optional.of(batch));
        when(itemRepository.save(any(SettlementItem.class))).thenAnswer(i -> i.getArgument(0));
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setCardType(CardType.DEBIT);
        card.setLinkedEntityId(UUID.randomUUID());
        AuthorizationLog auth = new AuthorizationLog();
        auth.setAmount(new BigDecimal("2500"));
        auth.setCurrencyCode("840");

        SettlementItem pending = service.addAuthorization(auth, card, "CARD-x", false);
        SettlementItem done = service.addAuthorization(auth, card, "CARD-y", true);

        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getAccountId()).isEqualTo(card.getLinkedEntityId());
        assertThat(pending.getHoldReference()).isEqualTo("CARD-x");
        assertThat(done.getStatus()).isEqualTo("SETTLED");
        verify(batchRepository, times(2)).addToTotals(eq(batch.getId()), any(BigDecimal.class));
    }

    @Test
    @DisplayName("markReversed: PENDING/SETTLED → REVERSED (MARKED); CLEARING untouched (IN_CLEARING); FAILED left alone (NONE)")
    void markReversed() {
        UUID auth = UUID.randomUUID();
        SettlementItem item = debitItem("PENDING");
        when(itemRepository.findByAuthorizationLogIdForUpdate(auth)).thenReturn(Optional.of(item));
        assertThat(service.markReversed(auth)).isEqualTo(SettlementService.ItemReversal.MARKED);
        assertThat(item.getStatus()).isEqualTo("REVERSED");

        UUID clearingAuth = UUID.randomUUID();
        SettlementItem inFlight = debitItem("CLEARING");
        when(itemRepository.findByAuthorizationLogIdForUpdate(clearingAuth)).thenReturn(Optional.of(inFlight));
        assertThat(service.markReversed(clearingAuth)).isEqualTo(SettlementService.ItemReversal.IN_CLEARING);
        assertThat(inFlight.getStatus()).isEqualTo("CLEARING");

        UUID failedAuth = UUID.randomUUID();
        SettlementItem failed = debitItem("FAILED");
        when(itemRepository.findByAuthorizationLogIdForUpdate(failedAuth)).thenReturn(Optional.of(failed));
        assertThat(service.markReversed(failedAuth)).isEqualTo(SettlementService.ItemReversal.NONE);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("reducePendingItem: PENDING drops by the reversal (to zero → REVERSED); CLEARING untouched; cleared → NOT_PENDING")
    void reducePendingItem() {
        UUID auth = UUID.randomUUID();
        SettlementItem item = debitItem("PENDING");   // 2500 minor units
        when(itemRepository.findByAuthorizationLogIdForUpdate(auth)).thenReturn(Optional.of(item));

        assertThat(service.reducePendingItem(auth, new BigDecimal("1000"))).isEqualTo(SettlementService.PendingReduction.REDUCED);
        assertThat(item.getAmount()).isEqualByComparingTo("1500");
        assertThat(service.reducePendingItem(auth, new BigDecimal("1500"))).isEqualTo(SettlementService.PendingReduction.REDUCED);
        assertThat(item.getStatus()).isEqualTo("REVERSED");

        UUID cleared = UUID.randomUUID();
        when(itemRepository.findByAuthorizationLogIdForUpdate(cleared)).thenReturn(Optional.of(debitItem("SETTLED")));
        assertThat(service.reducePendingItem(cleared, new BigDecimal("100"))).isEqualTo(SettlementService.PendingReduction.NOT_PENDING);

        UUID clearing = UUID.randomUUID();
        SettlementItem inFlight = debitItem("CLEARING");
        when(itemRepository.findByAuthorizationLogIdForUpdate(clearing)).thenReturn(Optional.of(inFlight));
        assertThat(service.reducePendingItem(clearing, new BigDecimal("100"))).isEqualTo(SettlementService.PendingReduction.IN_CLEARING);
        assertThat(inFlight.getAmount()).isEqualByComparingTo("2500");
    }

    // ── Closing and clearing ─────────────────────────────────────────────────

    @Test
    @DisplayName("closeBatch: items with nothing to clear on the backend settle; the batch ends CLOSED (for export)")
    void closeBatchSettles() {
        SettlementBatch batch = openBatch();
        SettlementItem legacy = new SettlementItem(); legacy.setId(UUID.randomUUID()); legacy.setStatus("PENDING");
        SettlementItem prepaid = new SettlementItem(); prepaid.setId(UUID.randomUUID());
        prepaid.setStatus("PENDING"); prepaid.setCardType("PREPAID");
        stubBatch(batch, legacy, prepaid);
        stubReread(legacy);
        stubReread(prepaid);
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        SettlementBatch closed = service.closeBatch(batch.getId());

        assertThat(closed.getStatus()).isEqualTo(SettlementBatchStatus.CLOSED);
        assertThat(legacy.getStatus()).isEqualTo("SETTLED");
        assertThat(prepaid.getStatus()).isEqualTo("SETTLED");
        verify(clearingClient, never()).capture(any(), any(), any(), any());
    }

    @Test
    @DisplayName("closeBatch clears a debit item on the backend in major units + alphabetic currency → SETTLED")
    void closeBatchClearsDebit() {
        SettlementBatch batch = openBatch();
        SettlementItem item = debitItem("PENDING");
        stubBatch(batch, item);
        stubReread(item);
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(clearingClient.capture(eq(item.getAccountId()), eq(item.getHoldReference()), any(BigDecimal.class), eq("USD")))
                .thenReturn(new BackendClearingClient.Result(true, "CAPTURED", null));

        service.closeBatch(batch.getId());

        verify(clearingClient).capture(eq(item.getAccountId()), eq(item.getHoldReference()),
                org.mockito.ArgumentMatchers.<BigDecimal>argThat(a -> a.compareTo(new BigDecimal("25.00")) == 0), eq("USD"));
        assertThat(item.getStatus()).isEqualTo("SETTLED");
        assertThat(item.getClearedAt()).isNotNull();
    }

    @Test
    @DisplayName("closeBatch: a refusal (late presentment without funds) is FAILED with the reason — never forced")
    void closeBatchRefusedFails() {
        SettlementBatch batch = openBatch();
        SettlementItem item = debitItem("PENDING");
        stubBatch(batch, item);
        stubReread(item);
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(clearingClient.capture(any(), any(), any(), any()))
                .thenReturn(new BackendClearingClient.Result(false, "EXPIRED", "INSUFFICIENT_FUNDS"));

        assertThat(service.closeBatch(batch.getId()).getStatus()).isEqualTo(SettlementBatchStatus.CLOSED);
        assertThat(item.getStatus()).isEqualTo("FAILED");
        assertThat(item.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("closeBatch: backend unreachable → the item stays CLEARING and the batch stays OPEN for a retry")
    void closeBatchBackendDownStaysOpen() {
        SettlementBatch batch = openBatch();
        SettlementItem item = debitItem("PENDING");
        stubBatch(batch, item);
        stubReread(item);
        when(clearingClient.capture(any(), any(), any(), any())).thenReturn(null);

        assertThat(service.closeBatch(batch.getId()).getStatus()).isEqualTo(SettlementBatchStatus.OPEN);
        // the capture may have committed: only a retried (idempotent) capture may resolve it
        assertThat(item.getStatus()).isEqualTo("CLEARING");
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("race: a reversal that changed the item before close claimed it wins — no capture, REVERSED kept")
    void closeBatchConcurrentReversalWins() {
        SettlementBatch batch = openBatch();
        SettlementItem read = debitItem("PENDING");
        read.setVersion(0L);
        SettlementItem reversed = debitItem("REVERSED");
        reversed.setId(read.getId());
        reversed.setVersion(1L);
        stubBatch(batch, read);
        when(itemRepository.findByIdForUpdate(read.getId())).thenReturn(Optional.of(reversed));
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        service.closeBatch(batch.getId());

        assertThat(reversed.getStatus()).isEqualTo("REVERSED");
        verify(clearingClient, never()).capture(any(), any(), any(), any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("an item left CLEARING by an interrupted close is captured again (idempotent) and resolved")
    void closeBatchRetriesClearing() {
        SettlementBatch batch = openBatch();
        SettlementItem item = debitItem("CLEARING");
        stubBatch(batch, item);
        stubReread(item);
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(clearingClient.capture(any(), any(), any(), any()))
                .thenReturn(new BackendClearingClient.Result(true, "CAPTURED", null));

        assertThat(service.closeBatch(batch.getId()).getStatus()).isEqualTo(SettlementBatchStatus.CLOSED);
        assertThat(item.getStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("closeBatch leaves already-resolved items alone (SETTLED 0200 / REVERSED)")
    void closeBatchSkipsResolved() {
        SettlementBatch batch = openBatch();
        SettlementItem done = debitItem("SETTLED");
        SettlementItem reversed = debitItem("REVERSED");
        stubBatch(batch, done, reversed);
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        service.closeBatch(batch.getId());
        verify(clearingClient, never()).capture(any(), any(), any(), any());
        assertThat(reversed.getStatus()).isEqualTo("REVERSED");
    }

    @Test
    @DisplayName("closeBatch leaves the batch OPEN while another close is still clearing one of its items")
    void closeBatchWaitsForInFlightClearing() {
        SettlementBatch batch = openBatch();
        SettlementItem read = debitItem("PENDING");
        SettlementItem claimedElsewhere = debitItem("CLEARING");
        claimedElsewhere.setId(read.getId());
        read.setVersion(0L);
        claimedElsewhere.setVersion(1L);
        stubBatch(batch, read);
        when(itemRepository.findByIdForUpdate(read.getId())).thenReturn(Optional.of(claimedElsewhere));
        when(itemRepository.countByBatchAndStatusIn(eq(batch), any())).thenReturn(1L);

        assertThat(service.closeBatch(batch.getId()).getStatus()).isEqualTo(SettlementBatchStatus.OPEN);
        verify(clearingClient, never()).capture(any(), any(), any(), any());
        verify(batchRepository, never()).save(any(SettlementBatch.class));
    }

    @Test
    @DisplayName("closeBatch rejects a batch that is not OPEN")
    void closeBatchRejectsNonOpen() {
        SettlementBatch batch = openBatch();
        batch.setStatus(SettlementBatchStatus.CLOSED);
        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.closeBatch(batch.getId())).isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("closeOpenBatches closes every OPEN batch up to today, and one failure doesn't stop the rest")
    void closeOpenBatches() {
        SettlementBatch a = openBatch();
        SettlementBatch b = openBatch();
        when(batchRepository.findByStatusAndSettlementDateLessThanEqual(eq(SettlementBatchStatus.OPEN), any(LocalDate.class)))
                .thenReturn(List.of(a, b));
        when(batchRepository.findById(a.getId())).thenThrow(new RuntimeException("boom"));
        stubBatch(b);
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        service.closeOpenBatches();
        assertThat(b.getStatus()).isEqualTo(SettlementBatchStatus.CLOSED);
    }

    // ── Expiry ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("expireUnmatchedAuthorizations marks stale PENDING items FAILED with a reason")
    void expireUnmatched() {
        SettlementItem stale = new SettlementItem(); stale.setStatus("PENDING");
        when(itemRepository.findExpiredPendingItems(any(OffsetDateTime.class))).thenReturn(List.of(stale));

        service.expireUnmatchedAuthorizations();

        assertThat(stale.getStatus()).isEqualTo("FAILED");
        assertThat(stale.getFailureReason()).isEqualTo("NOT_CLEARED_BEFORE_EXPIRY");
        verify(itemRepository).saveAll(List.of(stale));
    }

    @Test
    @DisplayName("expireUnmatchedAuthorizations does nothing when there are no stale items")
    void expireUnmatchedNoop() {
        when(itemRepository.findExpiredPendingItems(any(OffsetDateTime.class))).thenReturn(List.of());
        service.expireUnmatchedAuthorizations();
        verify(itemRepository, never()).saveAll(any());
    }
}

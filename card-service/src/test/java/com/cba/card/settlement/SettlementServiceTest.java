package com.cba.card.settlement;

import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.common.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for {@link SettlementService} — batch open/add/close and unmatched-auth expiry. */
@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock SettlementBatchRepository batchRepository;
    @Mock SettlementItemRepository itemRepository;
    @Mock AuthorizationLogRepository authLogRepository;
    @Mock BackendClearingClient clearingClient;

    @InjectMocks SettlementService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "authExpiryDays", 7);
    }

    private static SettlementBatch openBatch() {
        SettlementBatch b = new SettlementBatch();
        b.setBatchRef(UUID.randomUUID().toString());
        b.setSettlementDate(LocalDate.now());
        b.setStatus(SettlementBatchStatus.OPEN);
        return b; // totalAmount defaults to ZERO, itemCount to 0
    }

    @Test
    @DisplayName("openOrGetTodaysBatch returns the existing open batch without creating one")
    void returnsExistingOpenBatch() {
        SettlementBatch existing = openBatch();
        when(batchRepository.findBySettlementDateAndStatus(any(LocalDate.class), eq(SettlementBatchStatus.OPEN)))
                .thenReturn(Optional.of(existing));

        assertThat(service.openOrGetTodaysBatch()).isSameAs(existing);
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("openOrGetTodaysBatch creates a new OPEN batch when none exists")
    void createsNewBatch() {
        when(batchRepository.findBySettlementDateAndStatus(any(LocalDate.class), eq(SettlementBatchStatus.OPEN)))
                .thenReturn(Optional.empty());
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        SettlementBatch created = service.openOrGetTodaysBatch();

        assertThat(created.getStatus()).isEqualTo(SettlementBatchStatus.OPEN);
        assertThat(created.getBatchRef()).isNotBlank();
    }

    @Test
    @DisplayName("addToCurrentBatch records a PENDING item and updates batch totals")
    void addToCurrentBatch() {
        SettlementBatch batch = openBatch();
        when(batchRepository.findBySettlementDateAndStatus(any(LocalDate.class), eq(SettlementBatchStatus.OPEN)))
                .thenReturn(Optional.of(batch));
        when(itemRepository.save(any(SettlementItem.class))).thenAnswer(i -> i.getArgument(0));
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        SettlementItem item = service.addToCurrentBatch(UUID.randomUUID(), new BigDecimal("100.00"), "840");

        assertThat(item.getStatus()).isEqualTo("PENDING");
        assertThat(batch.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(batch.getItemCount()).isEqualTo(1);
    }

    private static SettlementItem debitItem(String status) {
        SettlementItem i = new SettlementItem();
        i.setStatus(status);
        i.setCardType("DEBIT");
        i.setAccountId(UUID.randomUUID());
        i.setHoldReference("CARD-" + UUID.randomUUID());
        i.setAmount(new BigDecimal("2500"));   // DE4 minor units
        i.setCurrencyCode("840");
        return i;
    }

    @Test
    @DisplayName("closeBatch: items with nothing to clear on the backend settle; the batch ends CLOSED (for export)")
    void closeBatchSettles() {
        UUID id = UUID.randomUUID();
        SettlementBatch batch = openBatch();
        SettlementItem item1 = new SettlementItem(); item1.setStatus("PENDING");
        SettlementItem prepaid = new SettlementItem(); prepaid.setStatus("PENDING"); prepaid.setCardType("PREPAID");
        when(batchRepository.findById(id)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatch(batch)).thenReturn(List.of(item1, prepaid));
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        SettlementBatch closed = service.closeBatch(id);

        assertThat(closed.getStatus()).isEqualTo(SettlementBatchStatus.CLOSED);
        assertThat(item1.getStatus()).isEqualTo("SETTLED");
        assertThat(prepaid.getStatus()).isEqualTo("SETTLED");
        verify(clearingClient, never()).capture(any(), any(), any(), any());
    }

    @Test
    @DisplayName("closeBatch clears a debit item on the backend in major units + alphabetic currency → SETTLED")
    void closeBatchClearsDebit() {
        UUID id = UUID.randomUUID();
        SettlementBatch batch = openBatch();
        SettlementItem item = debitItem("PENDING");
        when(batchRepository.findById(id)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatch(batch)).thenReturn(List.of(item));
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(clearingClient.capture(eq(item.getAccountId()), eq(item.getHoldReference()), any(BigDecimal.class), eq("USD")))
                .thenReturn(new BackendClearingClient.Result(true, "CAPTURED", null));

        service.closeBatch(id);

        verify(clearingClient).capture(eq(item.getAccountId()), eq(item.getHoldReference()),
                org.mockito.ArgumentMatchers.<BigDecimal>argThat(a -> a.compareTo(new BigDecimal("25.00")) == 0), eq("USD"));
        assertThat(item.getStatus()).isEqualTo("SETTLED");
        assertThat(item.getClearedAt()).isNotNull();
    }

    @Test
    @DisplayName("closeBatch: a refusal (late presentment without funds) is FAILED with the reason — never forced")
    void closeBatchRefusedFails() {
        UUID id = UUID.randomUUID();
        SettlementBatch batch = openBatch();
        SettlementItem item = debitItem("PENDING");
        when(batchRepository.findById(id)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatch(batch)).thenReturn(List.of(item));
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(clearingClient.capture(any(), any(), any(), any()))
                .thenReturn(new BackendClearingClient.Result(false, "EXPIRED", "INSUFFICIENT_FUNDS"));

        assertThat(service.closeBatch(id).getStatus()).isEqualTo(SettlementBatchStatus.CLOSED);
        assertThat(item.getStatus()).isEqualTo("FAILED");
        assertThat(item.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("closeBatch: backend unreachable → the item stays PENDING and the batch stays OPEN for a retry")
    void closeBatchBackendDownStaysOpen() {
        UUID id = UUID.randomUUID();
        SettlementBatch batch = openBatch();
        SettlementItem item = debitItem("PENDING");
        when(batchRepository.findById(id)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatch(batch)).thenReturn(List.of(item));
        when(clearingClient.capture(any(), any(), any(), any())).thenReturn(null);

        assertThat(service.closeBatch(id).getStatus()).isEqualTo(SettlementBatchStatus.OPEN);
        assertThat(item.getStatus()).isEqualTo("PENDING");
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("closeBatch leaves already-resolved items alone (SETTLED 0200 / REVERSED)")
    void closeBatchSkipsResolved() {
        UUID id = UUID.randomUUID();
        SettlementBatch batch = openBatch();
        SettlementItem done = debitItem("SETTLED");
        SettlementItem reversed = debitItem("REVERSED");
        when(batchRepository.findById(id)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatch(batch)).thenReturn(List.of(done, reversed));
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        service.closeBatch(id);
        verify(clearingClient, never()).capture(any(), any(), any(), any());
        assertThat(reversed.getStatus()).isEqualTo("REVERSED");
    }

    @Test
    @DisplayName("addAuthorization: a debit purchase is PENDING with its account + hold; a completed one SETTLED")
    void addAuthorization() {
        SettlementBatch batch = openBatch();
        when(batchRepository.findBySettlementDateAndStatus(any(LocalDate.class), eq(SettlementBatchStatus.OPEN)))
                .thenReturn(Optional.of(batch));
        when(itemRepository.save(any(SettlementItem.class))).thenAnswer(i -> i.getArgument(0));
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));
        com.cba.card.card.Card card = new com.cba.card.card.Card();
        card.setId(UUID.randomUUID());
        card.setCardType(com.cba.card.card.CardType.DEBIT);
        card.setLinkedEntityId(UUID.randomUUID());
        com.cba.card.auth.AuthorizationLog auth = new com.cba.card.auth.AuthorizationLog();
        auth.setAmount(new BigDecimal("2500"));
        auth.setCurrencyCode("840");

        SettlementItem pending = service.addAuthorization(auth, card, "CARD-x", false);
        SettlementItem done = service.addAuthorization(auth, card, "CARD-y", true);

        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getAccountId()).isEqualTo(card.getLinkedEntityId());
        assertThat(pending.getHoldReference()).isEqualTo("CARD-x");
        assertThat(done.getStatus()).isEqualTo("SETTLED");
        assertThat(batch.getItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("markReversed: a PENDING or SETTLED item becomes REVERSED; a FAILED one is left alone")
    void markReversed() {
        UUID auth = UUID.randomUUID();
        SettlementItem item = debitItem("PENDING");
        when(itemRepository.findByAuthorizationLogId(auth)).thenReturn(Optional.of(item));
        service.markReversed(auth);
        assertThat(item.getStatus()).isEqualTo("REVERSED");

        UUID failedAuth = UUID.randomUUID();
        SettlementItem failed = debitItem("FAILED");
        when(itemRepository.findByAuthorizationLogId(failedAuth)).thenReturn(Optional.of(failed));
        service.markReversed(failedAuth);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("closeOpenBatches closes every OPEN batch up to today, and one failure doesn't stop the rest")
    void closeOpenBatches() {
        SettlementBatch a = openBatch(); a.setId(UUID.randomUUID());
        SettlementBatch b = openBatch(); b.setId(UUID.randomUUID());
        when(batchRepository.findByStatusAndSettlementDateLessThanEqual(eq(SettlementBatchStatus.OPEN), any(LocalDate.class)))
                .thenReturn(List.of(a, b));
        when(batchRepository.findById(a.getId())).thenThrow(new RuntimeException("boom"));
        when(batchRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(itemRepository.findByBatch(b)).thenReturn(List.of());
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        service.closeOpenBatches();
        assertThat(b.getStatus()).isEqualTo(SettlementBatchStatus.CLOSED);
    }

    @Test
    @DisplayName("closeBatch rejects a batch that is not OPEN")
    void closeBatchRejectsNonOpen() {
        UUID id = UUID.randomUUID();
        SettlementBatch batch = openBatch();
        batch.setStatus(SettlementBatchStatus.SETTLED);
        when(batchRepository.findById(id)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.closeBatch(id)).isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("expireUnmatchedAuthorizations marks stale PENDING items FAILED")
    void expireUnmatched() {
        SettlementItem stale = new SettlementItem(); stale.setStatus("PENDING");
        when(itemRepository.findExpiredPendingItems(any(OffsetDateTime.class))).thenReturn(List.of(stale));

        service.expireUnmatchedAuthorizations();

        assertThat(stale.getStatus()).isEqualTo("FAILED");
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

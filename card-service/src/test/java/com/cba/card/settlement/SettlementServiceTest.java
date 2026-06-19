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

    @Test
    @DisplayName("closeBatch settles an OPEN batch and marks its items SETTLED")
    void closeBatchSettles() {
        UUID id = UUID.randomUUID();
        SettlementBatch batch = openBatch();
        SettlementItem item1 = new SettlementItem(); item1.setStatus("PENDING");
        SettlementItem item2 = new SettlementItem(); item2.setStatus("PENDING");
        when(batchRepository.findById(id)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatch(batch)).thenReturn(List.of(item1, item2));
        when(batchRepository.save(any(SettlementBatch.class))).thenAnswer(i -> i.getArgument(0));

        SettlementBatch settled = service.closeBatch(id);

        assertThat(settled.getStatus()).isEqualTo(SettlementBatchStatus.SETTLED);
        assertThat(item1.getStatus()).isEqualTo("SETTLED");
        assertThat(item2.getStatus()).isEqualTo("SETTLED");
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

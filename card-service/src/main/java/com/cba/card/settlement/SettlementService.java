package com.cba.card.settlement;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Settlement service — manages dual-message batch settlement and
 * single-message real-time advice recording.
 *
 * <p>Dual-message flow:
 * <ol>
 *   <li>Authorization (0100) places a fund hold — recorded as SettlementItem with status=PENDING</li>
 *   <li>End-of-day: batch close (0324) sets batch to CLOSED then SETTLED</li>
 *   <li>Nightly job reverses unmatched items older than {@code auth-expiry-days}</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementBatchRepository batchRepository;
    private final SettlementItemRepository  itemRepository;
    private final AuthorizationLogRepository authLogRepository;

    @Value("${card.settlement.auth-expiry-days:7}")
    private int authExpiryDays;

    // ── Batch Management ──────────────────────────────────────────────────────

    /**
     * Opens (or retrieves the existing open) settlement batch for today.
     * Called at start-of-day or on the first transaction of the day.
     */
    @Transactional
    public SettlementBatch openOrGetTodaysBatch() {
        LocalDate today = LocalDate.now();
        return batchRepository.findBySettlementDateAndStatus(today, SettlementBatchStatus.OPEN)
                .orElseGet(() -> {
                    SettlementBatch batch = new SettlementBatch();
                    batch.setBatchRef(UUID.randomUUID().toString());
                    batch.setSettlementDate(today);
                    batch.setStatus(SettlementBatchStatus.OPEN);
                    SettlementBatch saved = batchRepository.save(batch);
                    log.info("Opened settlement batch {} for {}", saved.getBatchRef(), today);
                    return saved;
                });
    }

    /**
     * Adds an approved authorization to the current open batch.
     * Called after every approved dual-message authorization (0100 → 0110 approved).
     */
    @Transactional
    public SettlementItem addToCurrentBatch(UUID authLogId, BigDecimal amount, String currencyCode) {
        SettlementBatch batch = openOrGetTodaysBatch();

        SettlementItem item = new SettlementItem();
        item.setBatch(batch);
        item.setAuthorizationLogId(authLogId);
        item.setAmount(amount);
        item.setCurrencyCode(currencyCode);
        item.setStatus("PENDING");

        SettlementItem saved = itemRepository.save(item);

        // Update batch totals
        batch.setTotalAmount(batch.getTotalAmount().add(amount));
        batch.setItemCount(batch.getItemCount() + 1);
        batchRepository.save(batch);

        return saved;
    }

    /**
     * Closes a batch (0324 Batch Close Request).
     * Sets status CLOSED → SETTLED and marks all items as SETTLED.
     */
    @Transactional
    public SettlementBatch closeBatch(UUID batchId) {
        SettlementBatch batch = findBatchById(batchId);
        if (batch.getStatus() != SettlementBatchStatus.OPEN) {
            throw CbaException.badRequest("INVALID_STATE",
                    "Only OPEN batches can be closed. Current status: " + batch.getStatus());
        }

        batch.setStatus(SettlementBatchStatus.CLOSED);
        batch.setClosedAt(OffsetDateTime.now());

        // Mark all pending items as SETTLED
        List<SettlementItem> items = itemRepository.findByBatch(batch);
        items.forEach(item -> item.setStatus("SETTLED"));
        itemRepository.saveAll(items);

        batch.setStatus(SettlementBatchStatus.SETTLED);
        SettlementBatch settled = batchRepository.save(batch);
        log.info("Settled batch {}: {} items, total={}",
                settled.getBatchRef(), settled.getItemCount(), settled.getTotalAmount());
        return settled;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SettlementBatch findBatchById(UUID id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("SETTLEMENT_BATCH_NOT_FOUND",
                        "Settlement batch not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SettlementBatch> listBatches(LocalDate date) {
        if (date != null) {
            return batchRepository.findBySettlementDateOrderByOpenedAtDesc(date);
        }
        return batchRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SettlementItem> getItems(UUID batchId) {
        SettlementBatch batch = findBatchById(batchId);
        return itemRepository.findByBatch(batch);
    }

    // ── Nightly CoB — Expire Unmatched Authorizations ─────────────────────────

    /**
     * Runs nightly at 23:58 — reverses any PENDING settlement items older than
     * {@code card.settlement.auth-expiry-days} (default 7).
     *
     * <p>Unmatched dual-message authorizations expire here. The corresponding
     * fund hold is automatically released.
     */
    @Scheduled(cron = "0 58 23 * * *")
    @Transactional
    public void expireUnmatchedAuthorizations() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(authExpiryDays);
        List<SettlementItem> expired = itemRepository.findExpiredPendingItems(cutoff);

        if (expired.isEmpty()) return;

        expired.forEach(item -> item.setStatus("FAILED"));
        itemRepository.saveAll(expired);
        log.info("CoB: expired {} unmatched settlement items older than {} days",
                expired.size(), authExpiryDays);
    }
}

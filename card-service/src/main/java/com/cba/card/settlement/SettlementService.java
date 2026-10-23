package com.cba.card.settlement;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.auth.CardAuthorizationService;
import com.cba.card.card.Card;
import com.cba.card.card.CardType;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Settlement service — the day's batch of approved card transactions and their clearing.
 *
 * <p>Flow (backlog 15a — clearing posts to the GL):
 * <ol>
 *   <li>Every approval that reserved funds adds an item, in the approval's transaction
 *       ({@link #addAuthorization}): a debit-card purchase (0100) is PENDING; a single-message debit
 *       (0200) and a prepaid approval are already completed, so they start SETTLED</li>
 *   <li>Batch close (scheduled nightly, or manual) clears each PENDING debit item on the backend:
 *       its hold becomes a debit and DR deposit / CR 2300 Card Scheme Settlement Payable posts. A
 *       refusal (e.g. a late presentment that no longer has the funds) is FAILED with the reason —
 *       never force-posted. The batch ends CLOSED, which the nightly file export picks up. If the
 *       backend can't be reached the batch stays OPEN and the next close retries (clearing is
 *       idempotent on the backend)</li>
 *   <li>A reversal marks its item REVERSED ({@link #markReversed})</li>
 * </ol>
 * Each item is cleared in its own short transaction, with no transaction held across the backend
 * call, and is re-read before its outcome is written: an item a reversal (or a second close) changed
 * meanwhile is left as that change made it ({@code @Version} catches a simultaneous write).
 *
 * <p>There is no scheme clearing-file intake yet: the batch close presents each purchase at its
 * authorized amount (user decision 1a, backlog 15). Interchange, scheme fees and the scheme's
 * cash settlement are backlog 15b; SETTLED for the batch is set there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementBatchRepository batchRepository;
    private final SettlementItemRepository  itemRepository;
    private final AuthorizationLogRepository authLogRepository;
    private final BackendClearingClient     clearingClient;
    private final PlatformTransactionManager transactionManager;

    @Value("${card.settlement.auth-expiry-days:7}")
    private int authExpiryDays;

    // ── Batch Management ──────────────────────────────────────────────────────

    /**
     * Opens (or retrieves the existing open) settlement batch for today. Every approval calls this, so
     * the batch is created with {@code ON CONFLICT DO NOTHING} against the one-OPEN-batch-per-date index:
     * two first approvals of the day share one batch instead of creating two.
     */
    @Transactional
    public SettlementBatch openOrGetTodaysBatch() {
        LocalDate today = LocalDate.now();
        return batchRepository.findBySettlementDateAndStatus(today, SettlementBatchStatus.OPEN)
                .orElseGet(() -> {
                    batchRepository.insertOpenBatchIfAbsent(today);
                    SettlementBatch batch = batchRepository.findBySettlementDateAndStatus(today, SettlementBatchStatus.OPEN)
                            .orElseThrow(() -> new IllegalStateException("No OPEN settlement batch for " + today));
                    log.info("Opened settlement batch {} for {}", batch.getBatchRef(), today);
                    return batch;
                });
    }

    /**
     * Records an approval that reserved funds, in the approval's own transaction — so an approval
     * never exists without the item that will clear it.
     *
     * @param holdReference the backend hold (debit purchase) or single-message debit; null for prepaid
     * @param completed     true when the approval already moved the money (0200 debit, prepaid wallet)
     */
    @Transactional
    public SettlementItem addAuthorization(AuthorizationLog auth, Card card, String holdReference, boolean completed) {
        SettlementItem item = new SettlementItem();
        item.setAuthorizationLogId(auth.getId());
        item.setAmount(auth.getAmount());
        item.setCurrencyCode(auth.getCurrencyCode());
        item.setCardId(card.getId());
        item.setCardType(card.getCardType().name());
        item.setAccountId(card.getCardType() == CardType.DEBIT ? card.getLinkedEntityId() : null);
        item.setHoldReference(holdReference);
        item.setStatus(completed ? "SETTLED" : "PENDING");
        item.setClearedAt(completed ? OffsetDateTime.now() : null);
        return addItem(item);
    }

    /** Adds a PENDING item for an authorization log entry (manual/legacy path). */
    @Transactional
    public SettlementItem addToCurrentBatch(UUID authLogId, BigDecimal amount, String currencyCode) {
        SettlementItem item = new SettlementItem();
        item.setAuthorizationLogId(authLogId);
        item.setAmount(amount);
        item.setCurrencyCode(currencyCode);
        item.setStatus("PENDING");
        return addItem(item);
    }

    /** Saves the item and adds it to the batch totals in one UPDATE (no version conflict between approvals). */
    private SettlementItem addItem(SettlementItem item) {
        SettlementBatch batch = openOrGetTodaysBatch();
        item.setBatch(batch);
        SettlementItem saved = itemRepository.save(item);
        batchRepository.addToTotals(batch.getId(), item.getAmount());
        return saved;
    }

    /**
     * The approval was reversed in full: its item no longer settles. The backend hold was released (or
     * the completed debit refunded) by the reversal itself. Batch totals keep the submitted figures.
     */
    @Transactional
    public void markReversed(UUID authLogId) {
        if (authLogId == null) return;
        itemRepository.findByAuthorizationLogId(authLogId)
                .filter(item -> Set.of("PENDING", "SETTLED").contains(item.getStatus()))
                .ifPresent(item -> {
                    item.setStatus("REVERSED");
                    itemRepository.save(item);
                });
    }

    /**
     * Closes a batch: clears each PENDING item (backend capture for debit cards), then marks the
     * batch CLOSED for the file export. Items the backend refuses are FAILED with its reason. If
     * the backend can't be reached for any item, the batch stays OPEN and a later close retries.
     * Not one transaction: each item is cleared and written on its own (see the class comment).
     */
    public SettlementBatch closeBatch(UUID batchId) {
        SettlementBatch batch = findBatchById(batchId);
        if (batch.getStatus() != SettlementBatchStatus.OPEN) {
            throw CbaException.badRequest("INVALID_STATE",
                    "Only OPEN batches can be closed. Current status: " + batch.getStatus());
        }

        List<SettlementItem> pending = itemRepository.findByBatch(batch).stream()
                .filter(item -> "PENDING".equals(item.getStatus()))
                .toList();
        int unreachable = 0;
        for (SettlementItem item : pending) {
            try {
                if (!clear(item)) unreachable++;
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                // Changed at the same moment (a reversal, or a second close): that change stands.
                log.info("Settlement item {} changed while being cleared; left as it is", item.getId());
            }
        }
        if (unreachable > 0) {
            log.warn("Batch {} left OPEN: {} items could not reach the backend; the next close retries",
                    batch.getBatchRef(), unreachable);
            return batch;
        }

        return new TransactionTemplate(transactionManager).execute(status -> {
            SettlementBatch fresh = findBatchById(batchId);
            if (fresh.getStatus() != SettlementBatchStatus.OPEN) return fresh;   // closed meanwhile
            fresh.setStatus(SettlementBatchStatus.CLOSED);
            fresh.setClosedAt(OffsetDateTime.now());
            SettlementBatch closed = batchRepository.save(fresh);
            log.info("Closed batch {}: {} items, total={}",
                    closed.getBatchRef(), closed.getItemCount(), closed.getTotalAmount());
            return closed;
        });
    }

    /**
     * Clears one PENDING item: the backend call runs outside any transaction, then the outcome is
     * written only if the item is unchanged since it was read. True when resolved (SETTLED, FAILED, or
     * changed by someone else), false when the backend couldn't be reached and it stays PENDING.
     */
    private boolean clear(SettlementItem item) {
        String status;
        String reason = null;
        if (!CardType.DEBIT.name().equals(item.getCardType())
                || item.getHoldReference() == null || item.getAccountId() == null) {
            status = "SETTLED";   // nothing to clear on the backend (prepaid, or recorded without a hold)
        } else {
            Currency currency = CardAuthorizationService.currency(item.getCurrencyCode());
            if (currency == null || item.getAmount() == null) {
                status = "FAILED";
                reason = "UNKNOWN_CURRENCY";
            } else {
                BackendClearingClient.Result result = clearingClient.capture(item.getAccountId(), item.getHoldReference(),
                        CardAuthorizationService.toMajorUnits(item.getAmount(), currency), currency.getCurrencyCode());
                if (result == null) return false;
                status = result.approved() ? "SETTLED" : "FAILED";
                reason = result.approved() ? null : result.reason();
            }
        }
        record(item, status, reason);
        return true;
    }

    /** Writes a clearing outcome in its own transaction, unless the item changed since it was read. */
    private void record(SettlementItem read, String status, String reason) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            SettlementItem item = itemRepository.findById(read.getId()).orElse(null);
            if (item == null || !"PENDING".equals(item.getStatus()) || !Objects.equals(item.getVersion(), read.getVersion())) {
                log.info("Settlement item {} changed while being cleared; left {}", read.getId(),
                        item == null ? "missing" : item.getStatus());
                return;
            }
            item.setStatus(status);
            item.setFailureReason(reason);
            if ("SETTLED".equals(status)) item.setClearedAt(OffsetDateTime.now());
            itemRepository.save(item);
            if (reason != null) {
                log.warn("Clearing refused for settlement item {} (hold {}): {}", item.getId(), item.getHoldReference(), reason);
            }
        });
    }

    /**
     * Nightly batch close (default 23:57, before the 23:58 file export): every OPEN batch up to
     * today, so a batch left open because the backend was unreachable is retried.
     */
    @Scheduled(cron = "${card.settlement.close-cron:0 57 23 * * *}")
    public void closeOpenBatches() {
        for (SettlementBatch batch : batchRepository.findByStatusAndSettlementDateLessThanEqual(
                SettlementBatchStatus.OPEN, LocalDate.now())) {
            try {
                closeBatch(batch.getId());
            } catch (Exception e) {
                log.error("Closing batch {} failed: {}", batch.getBatchRef(), e.getMessage());
            }
        }
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
     * Runs nightly at 23:58 — marks FAILED any item still PENDING after
     * {@code card.settlement.auth-expiry-days} (default 7): it could not be cleared that long
     * (backend unreachable at every close). Its backend hold has expired by then and stopped
     * reserving funds; clearing it later would be a late presentment, decided by operations.
     */
    @Scheduled(cron = "0 58 23 * * *")
    @Transactional
    public void expireUnmatchedAuthorizations() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(authExpiryDays);
        List<SettlementItem> expired = itemRepository.findExpiredPendingItems(cutoff);

        if (expired.isEmpty()) return;

        expired.forEach(item -> {
            item.setStatus("FAILED");
            item.setFailureReason("NOT_CLEARED_BEFORE_EXPIRY");
        });
        itemRepository.saveAll(expired);
        log.info("CoB: expired {} unmatched settlement items older than {} days",
                expired.size(), authExpiryDays);
    }
}

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
     * A partial reversal of a purchase not yet cleared: lower the amount batch close will clear (the
     * backend's partial capture then frees the rest of the hold). False when the item isn't PENDING —
     * already cleared or completed — so the caller refunds instead.
     */
    @Transactional
    public PendingReduction reducePendingItem(UUID authLogId, BigDecimal reversalMinor) {
        if (authLogId == null || reversalMinor == null) return PendingReduction.NOT_PENDING;
        SettlementItem item = itemRepository.findByAuthorizationLogIdForUpdate(authLogId).orElse(null);
        if (item == null || item.getAmount() == null) return PendingReduction.NOT_PENDING;
        // Being cleared right now: the backend may be capturing the original amount. Don't touch it —
        // the caller answers "try again", and the retry refunds the cleared debit on the backend.
        if (CLEARING.equals(item.getStatus())) return PendingReduction.IN_CLEARING;
        if (!"PENDING".equals(item.getStatus())) return PendingReduction.NOT_PENDING;
        BigDecimal rest = item.getAmount().subtract(reversalMinor);
        if (rest.signum() <= 0) {
            item.setStatus("REVERSED");
        } else {
            item.setAmount(rest);
        }
        itemRepository.save(item);   // @Version: a close that read the item before this can't move it to CLEARING
        return PendingReduction.REDUCED;
    }

    /** Outcome of {@link #reducePendingItem}. */
    public enum PendingReduction {
        /** The item was PENDING: its amount dropped (the hold stays for the rest). */
        REDUCED,
        /** The item is being cleared at this moment: retry the reversal. */
        IN_CLEARING,
        /** No PENDING item (already cleared, completed, or none): release / refund on the backend. */
        NOT_PENDING
    }

    /** In-flight clearing: set before the backend capture call, so a partial reversal can't race it. */
    static final String CLEARING = "CLEARING";

    /**
     * The approval is being reversed in full: its item no longer settles. Runs in the reversal's
     * transaction <em>before</em> the backend hold is released, with the row locked until the reversal
     * commits — so a batch close can't claim (and capture) it in between. If the reversal then fails
     * (backend unreachable / refused) it rolls back and the item is PENDING (or SETTLED) again. An item
     * being cleared at this moment is left alone: the caller answers "try again". Batch totals keep the
     * submitted figures.
     */
    @Transactional
    public ItemReversal markReversed(UUID authLogId) {
        if (authLogId == null) return ItemReversal.NONE;
        SettlementItem item = itemRepository.findByAuthorizationLogIdForUpdate(authLogId).orElse(null);
        if (item == null) return ItemReversal.NONE;
        if (CLEARING.equals(item.getStatus())) return ItemReversal.IN_CLEARING;
        if (!Set.of("PENDING", "SETTLED").contains(item.getStatus())) return ItemReversal.NONE;
        item.setStatus("REVERSED");
        itemRepository.save(item);
        return ItemReversal.MARKED;
    }

    /** Outcome of {@link #markReversed}. */
    public enum ItemReversal {
        /** The item is now REVERSED (rolls back with the reversal if that fails). */
        MARKED,
        /** The item is being cleared at this moment: retry the reversal. */
        IN_CLEARING,
        /** No item to reverse (none, FAILED, or already REVERSED). */
        NONE
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

        // CLEARING items are retried too: an earlier close may have died (or lost the backend's answer)
        // mid-capture; the backend capture is idempotent.
        List<SettlementItem> pending = itemRepository.findByBatch(batch).stream()
                .filter(item -> "PENDING".equals(item.getStatus()) || CLEARING.equals(item.getStatus()))
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
            // Another close may be capturing an item right now (this close saw its claim as "changed"):
            // the batch closes only once nothing in it is PENDING or CLEARING, or the export would miss it.
            long unresolved = itemRepository.countByBatchAndStatusIn(fresh, List.of("PENDING", CLEARING));
            if (unresolved > 0) {
                log.info("Batch {} left OPEN: {} items still pending or being cleared", fresh.getBatchRef(), unresolved);
                return fresh;
            }
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
    private boolean clear(SettlementItem read) {
        // Claim the item first (PENDING → CLEARING, version-checked): from here a partial reversal waits
        // for the outcome instead of lowering an amount the backend may already be capturing.
        SettlementItem item = claim(read);
        if (item == null) return true;   // changed meanwhile (reversed / cleared by another close)
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

    /**
     * Moves an item to CLEARING in its own transaction if it is unchanged since it was read (still PENDING,
     * or CLEARING from an interrupted close). Null when it changed — the other change stands. A backend
     * that can't be reached leaves it CLEARING: the capture may have committed, so only a retried
     * capture (idempotent) may resolve it — never a local amount change.
     */
    private SettlementItem claim(SettlementItem read) {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            SettlementItem item = itemRepository.findByIdForUpdate(read.getId()).orElse(null);
            if (item == null || !Objects.equals(item.getVersion(), read.getVersion())
                    || !("PENDING".equals(item.getStatus()) || CLEARING.equals(item.getStatus()))) {
                log.info("Settlement item {} changed before clearing; left {}", read.getId(),
                        item == null ? "missing" : item.getStatus());
                return null;
            }
            item.setStatus(CLEARING);
            itemRepository.save(item);
            return item;
        });
    }

    /** Writes a clearing outcome in its own transaction, unless the item changed since it was claimed. */
    private void record(SettlementItem read, String status, String reason) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            SettlementItem item = itemRepository.findByIdForUpdate(read.getId()).orElse(null);
            if (item == null || !CLEARING.equals(item.getStatus()) || !Objects.equals(item.getVersion(), read.getVersion())) {
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

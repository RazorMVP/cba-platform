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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
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
 * There is no scheme clearing-file intake yet: the batch close presents each purchase at its
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

    private SettlementItem addItem(SettlementItem item) {
        SettlementBatch batch = openOrGetTodaysBatch();
        item.setBatch(batch);
        SettlementItem saved = itemRepository.save(item);
        batch.setTotalAmount(batch.getTotalAmount().add(item.getAmount()));
        batch.setItemCount(batch.getItemCount() + 1);
        batchRepository.save(batch);
        return saved;
    }

    /**
     * The approval was reversed: its item no longer settles. The backend hold was released (or the
     * completed debit refunded) by the reversal itself. Batch totals keep the submitted figures.
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
     */
    @Transactional
    public SettlementBatch closeBatch(UUID batchId) {
        SettlementBatch batch = findBatchById(batchId);
        if (batch.getStatus() != SettlementBatchStatus.OPEN) {
            throw CbaException.badRequest("INVALID_STATE",
                    "Only OPEN batches can be closed. Current status: " + batch.getStatus());
        }

        int unreachable = 0;
        for (SettlementItem item : itemRepository.findByBatch(batch)) {
            if ("PENDING".equals(item.getStatus()) && !clear(item)) {
                unreachable++;
            }
        }
        if (unreachable > 0) {
            log.warn("Batch {} left OPEN: {} items could not reach the backend; the next close retries",
                    batch.getBatchRef(), unreachable);
            return batch;
        }

        batch.setStatus(SettlementBatchStatus.CLOSED);
        batch.setClosedAt(OffsetDateTime.now());
        SettlementBatch closed = batchRepository.save(batch);
        log.info("Closed batch {}: {} items, total={}",
                closed.getBatchRef(), closed.getItemCount(), closed.getTotalAmount());
        return closed;
    }

    /**
     * Clears one PENDING item. True when it is resolved (SETTLED or FAILED), false when the backend
     * couldn't be reached and it stays PENDING. Items with nothing to clear on the backend (prepaid,
     * or items recorded without a hold) settle as they are.
     */
    private boolean clear(SettlementItem item) {
        if (!CardType.DEBIT.name().equals(item.getCardType())
                || item.getHoldReference() == null || item.getAccountId() == null) {
            settled(item);
            return true;
        }
        Currency currency = CardAuthorizationService.currency(item.getCurrencyCode());
        if (currency == null || item.getAmount() == null) {
            failed(item, "UNKNOWN_CURRENCY");
            return true;
        }
        BackendClearingClient.Result result = clearingClient.capture(item.getAccountId(), item.getHoldReference(),
                CardAuthorizationService.toMajorUnits(item.getAmount(), currency), currency.getCurrencyCode());
        if (result == null) {
            return false;
        }
        if (result.approved()) {
            settled(item);
        } else {
            failed(item, result.reason());
        }
        return true;
    }

    private void settled(SettlementItem item) {
        item.setStatus("SETTLED");
        item.setClearedAt(OffsetDateTime.now());
        itemRepository.save(item);
    }

    private void failed(SettlementItem item, String reason) {
        item.setStatus("FAILED");
        item.setFailureReason(reason);
        itemRepository.save(item);
        log.warn("Clearing refused for settlement item {} (hold {}): {}", item.getId(), item.getHoldReference(), reason);
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

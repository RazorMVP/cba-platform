package com.cba.card.settlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementItemRepository extends JpaRepository<SettlementItem, UUID> {
    List<SettlementItem> findByBatch(SettlementBatch batch);

    @Query("SELECT si FROM SettlementItem si WHERE si.status = 'PENDING' AND si.createdAt < :cutoff")
    List<SettlementItem> findExpiredPendingItems(OffsetDateTime cutoff);

    /** The item an approval produced (read only — writers use {@link #findByAuthorizationLogIdForUpdate}). */
    Optional<SettlementItem> findByAuthorizationLogId(UUID authorizationLogId);

    /**
     * The item an approval produced, row-locked: a reversal holds the lock until it commits, so a batch
     * close that claims the item at the same moment waits and then sees the reversal's change.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM SettlementItem si WHERE si.authorizationLogId = :authorizationLogId")
    Optional<SettlementItem> findByAuthorizationLogIdForUpdate(UUID authorizationLogId);

    /** Row-locked read for batch close's claim / outcome writes (waits for a reversal holding the row). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM SettlementItem si WHERE si.id = :id")
    Optional<SettlementItem> findByIdForUpdate(UUID id);

    /** Items of a batch still waiting for (or in the middle of) clearing. */
    long countByBatchAndStatusIn(SettlementBatch batch, Collection<String> statuses);
}

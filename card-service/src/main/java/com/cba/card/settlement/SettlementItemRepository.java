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

    /** Cleared items of a scheme and settlement date not yet in a scheme report (backlog 15b). */
    @Query("SELECT i FROM SettlementItem i WHERE i.scheme = :scheme AND i.batch.settlementDate = :date "
            + "AND i.glNet IS NOT NULL AND i.settlementReportId IS NULL")
    List<SettlementItem> findUnreportedCleared(String scheme, java.time.LocalDate date);

    /** Refunds of a scheme made on a date, not yet in a scheme report. */
    @Query("SELECT i FROM SettlementItem i WHERE i.scheme = :scheme AND i.refundSettlementDate = :date "
            + "AND i.refundGlNet IS NOT NULL AND i.refundReportId IS NULL")
    List<SettlementItem> findUnreportedRefunds(String scheme, java.time.LocalDate date);

    List<SettlementItem> findBySettlementReportId(UUID reportId);

    List<SettlementItem> findByRefundReportId(UUID reportId);

    /** Items of a settlement date in the given statuses (e.g. still PENDING or CLEARING). */
    @Query("SELECT COUNT(i) FROM SettlementItem i WHERE i.batch.settlementDate = :date AND i.status IN :statuses")
    long countByDateAndStatusIn(java.time.LocalDate date, Collection<String> statuses);

    /** Items of a batch that moved the GL but aren't in a recorded scheme report yet. */
    @Query("SELECT COUNT(i) FROM SettlementItem i WHERE i.batch = :batch AND i.glNet IS NOT NULL AND "
            + "(i.settlementReportId IS NULL OR i.settlementReportId NOT IN "
            + "(SELECT r.id FROM SchemeSettlementReport r WHERE r.status = 'RECORDED'))")
    long countUnsettledGlItems(SettlementBatch batch);
}

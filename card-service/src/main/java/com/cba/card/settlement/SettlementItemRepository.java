package com.cba.card.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementItemRepository extends JpaRepository<SettlementItem, UUID> {
    List<SettlementItem> findByBatch(SettlementBatch batch);

    @Query("SELECT si FROM SettlementItem si WHERE si.status = 'PENDING' AND si.createdAt < :cutoff")
    List<SettlementItem> findExpiredPendingItems(OffsetDateTime cutoff);
}

package com.cba.card.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementRefundRepository extends JpaRepository<SettlementRefund, UUID> {

    Optional<SettlementRefund> findByReversalReference(String reversalReference);

    /** Refunds of a scheme made on a date, not yet in a scheme report (with their items, for the currency). */
    @Query("SELECT r FROM SettlementRefund r JOIN FETCH r.item i WHERE i.scheme = :scheme "
            + "AND r.refundSettlementDate = :date AND r.refundReportId IS NULL")
    List<SettlementRefund> findUnreported(String scheme, LocalDate date);

    List<SettlementRefund> findByRefundReportId(UUID reportId);

    /** The refunds of one item, oldest first. */
    @Query("SELECT r FROM SettlementRefund r WHERE r.item.id = :itemId ORDER BY r.createdAt")
    List<SettlementRefund> findByItemId(UUID itemId);
}

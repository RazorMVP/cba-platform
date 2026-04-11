package com.cba.card.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {
    Optional<SettlementBatch> findBySettlementDateAndStatus(LocalDate date, SettlementBatchStatus status);
    Optional<SettlementBatch> findByBatchRef(String batchRef);
    List<SettlementBatch> findBySettlementDateOrderByOpenedAtDesc(LocalDate date);
    List<SettlementBatch> findByStatus(SettlementBatchStatus status);

    /** Find all batches in a given status on a specific settlement date — used by nightly exporter. */
    List<SettlementBatch> findByStatusAndSettlementDate(SettlementBatchStatus status, LocalDate date);
}

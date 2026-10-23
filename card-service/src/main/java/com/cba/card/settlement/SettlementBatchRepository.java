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

    /**
     * Creates the OPEN batch for a date unless one exists (unique partial index, V12) — safe when two
     * approvals open the day at once.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            INSERT INTO settlement_batches (id, batch_ref, status, settlement_date)
            VALUES (gen_random_uuid(), gen_random_uuid()::text, 'OPEN', :date)
            ON CONFLICT (settlement_date) WHERE status = 'OPEN' DO NOTHING""", nativeQuery = true)
    int insertOpenBatchIfAbsent(LocalDate date);

    /**
     * Adds one item to a batch's totals in one statement (row lock, no read-modify-write), so concurrent
     * approvals don't collide on the batch's version.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE SettlementBatch b SET b.totalAmount = b.totalAmount + :amount, "
            + "b.itemCount = b.itemCount + 1, b.version = b.version + 1 WHERE b.id = :id")
    int addToTotals(UUID id, java.math.BigDecimal amount);

    /** OPEN batches up to a date — the scheduled close also retries earlier batches left open. */
    List<SettlementBatch> findByStatusAndSettlementDateLessThanEqual(SettlementBatchStatus status, LocalDate date);
}

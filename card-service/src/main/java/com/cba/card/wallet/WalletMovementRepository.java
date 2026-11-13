package com.cba.card.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletMovementRepository extends JpaRepository<WalletMovement, UUID> {

    List<WalletMovement> findTop50ByCardIdOrderByCreatedAtDesc(UUID cardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM WalletMovement m WHERE m.id = :id")
    Optional<WalletMovement> findByIdForUpdate(UUID id);

    /** PENDING movements due for another attempt (their answer was lost or the backend was down). */
    @Query("SELECT m FROM WalletMovement m WHERE m.status = 'PENDING' AND (m.nextRetryAt IS NULL OR m.nextRetryAt <= :now) "
            + "ORDER BY m.createdAt")
    List<WalletMovement> findDue(OffsetDateTime now);

    /**
     * Serialises loads of one card until the transaction ends ({@code pg_advisory_xact_lock}) — also the first
     * load, when there is no wallet row to lock yet — so two loads can't both pass the limits.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('wallet:' || CAST(:cardId AS text)))", nativeQuery = true)
    Integer lockCardLoads(UUID cardId);

    /** Loads not answered yet (major units) — counted against the balance limit as if credited. */
    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM WalletMovement m "
            + "WHERE m.cardId = :cardId AND m.movementType = 'LOAD' AND m.status = 'PENDING'")
    BigDecimal sumPendingLoads(UUID cardId);

    /** Loads claimed since {@code since}, pending or completed (a refused one moved nothing) — the daily limit. */
    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM WalletMovement m "
            + "WHERE m.cardId = :cardId AND m.movementType = 'LOAD' AND m.status <> 'FAILED' AND m.createdAt >= :since")
    BigDecimal sumLoadsSince(UUID cardId, OffsetDateTime since);
}

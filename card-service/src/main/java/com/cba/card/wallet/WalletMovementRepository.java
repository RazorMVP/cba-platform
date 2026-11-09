package com.cba.card.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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
}

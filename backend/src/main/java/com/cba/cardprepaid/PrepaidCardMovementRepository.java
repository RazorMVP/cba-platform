package com.cba.cardprepaid;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PrepaidCardMovementRepository extends JpaRepository<PrepaidCardMovement, UUID> {

    Optional<PrepaidCardMovement> findByReference(String reference);

    /** A clearing, locked: its one refund is decided under the lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM PrepaidCardMovement m WHERE m.reference = :reference")
    Optional<PrepaidCardMovement> findByReferenceForUpdate(@Param("reference") String reference);
}

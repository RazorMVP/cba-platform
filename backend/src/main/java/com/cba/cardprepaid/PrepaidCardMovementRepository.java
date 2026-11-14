package com.cba.cardprepaid;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrepaidCardMovementRepository extends JpaRepository<PrepaidCardMovement, UUID> {

    Optional<PrepaidCardMovement> findByReference(String reference);

    /** A clearing, locked: each refund of it is decided under the lock, so refunds never pass the cleared amount. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM PrepaidCardMovement m WHERE m.reference = :reference")
    Optional<PrepaidCardMovement> findByReferenceForUpdate(@Param("reference") String reference);

    /** The refunds of a clearing so far (their interchange and fee shares add up to what has been reversed). */
    @Query("SELECT m FROM PrepaidCardMovement m WHERE m.type = com.cba.cardprepaid.PrepaidCardMovement.Type.REFUND "
            + "AND m.originalReference = :clearingReference")
    List<PrepaidCardMovement> findRefundsOf(@Param("clearingReference") String clearingReference);
}

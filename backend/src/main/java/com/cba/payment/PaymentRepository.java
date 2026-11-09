package com.cba.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Page<Payment> findBySourceAccountId(UUID accountId, Pageable pageable);
    Page<Payment> findBySourceAccountIdOrDestinationAccountId(UUID srcId, UUID dstId, Pageable pageable);
    Page<Payment> findByPaymentType(PaymentType paymentType, Pageable pageable);
    Page<Payment> findByPaymentTypeAndStatus(PaymentType paymentType, PaymentStatus status, Pageable pageable);

    /** The payment row, locked: two outcomes for one external payment are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.referenceNumber = :reference")
    Optional<Payment> findByReferenceForUpdate(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.networkReference = :networkReference")
    Optional<Payment> findByNetworkReferenceForUpdate(@Param("networkReference") String networkReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
}

package com.cba.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Page<Payment> findBySourceAccountId(UUID accountId, Pageable pageable);
    Page<Payment> findBySourceAccountIdOrDestinationAccountId(UUID srcId, UUID dstId, Pageable pageable);
    Page<Payment> findByPaymentType(PaymentType paymentType, Pageable pageable);
}

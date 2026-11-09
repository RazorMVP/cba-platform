package com.cba.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExternalPaymentCallbackRepository extends JpaRepository<ExternalPaymentCallback, UUID> {
    List<ExternalPaymentCallback> findByPaymentIdOrderByReceivedAtDesc(UUID paymentId);
}

package com.cba.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardHoldRefundRepository extends JpaRepository<CardHoldRefund, UUID> {

    /** A refund by its reversal reference (unique across holds). */
    Optional<CardHoldRefund> findByRefundReference(String refundReference);

    List<CardHoldRefund> findByHoldIdOrderByCreatedAtAsc(UUID holdId);
}

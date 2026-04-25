package com.cba.partner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PartnerWebhookDeliveryRepository extends JpaRepository<PartnerWebhookDelivery, UUID> {

    List<PartnerWebhookDelivery> findByWebhookIdOrderByCreatedAtDesc(UUID webhookId);

    @Query("SELECT d FROM PartnerWebhookDelivery d " +
           "WHERE d.status = 'FAILED' AND d.attemptCount < 5 AND d.nextRetryAt <= :now")
    List<PartnerWebhookDelivery> findDueForRetry(@Param("now") Instant now);
}

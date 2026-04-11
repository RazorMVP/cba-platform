package com.cba.card.openbanking.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {

    List<WebhookDeliveryLog> findByWebhookIdOrderByCreatedAtDesc(UUID webhookId);

    /** Pick up FAILED deliveries ready for retry (next_retry_at has passed, attempts < 5). */
    @Query("""
           SELECT d FROM WebhookDeliveryLog d
           WHERE d.status = 'FAILED'
             AND d.attemptCount < 5
             AND d.nextRetryAt <= :now
           ORDER BY d.nextRetryAt ASC
           """)
    List<WebhookDeliveryLog> findDueForRetry(OffsetDateTime now);
}

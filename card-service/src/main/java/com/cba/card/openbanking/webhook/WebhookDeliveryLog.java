package com.cba.card.openbanking.webhook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Delivery attempt record for a single webhook event dispatch.
 *
 * <h3>Status lifecycle</h3>
 * <pre>
 *   PENDING → DELIVERED
 *           ↘ FAILED (after 5 attempts with exponential backoff)
 * </pre>
 *
 * <h3>Retry schedule (backoff delays)</h3>
 * Attempt 1 → 15 s → Attempt 2 → 60 s → Attempt 3 → 5 min → Attempt 4 → 30 min → Attempt 5 → FAILED
 */
@Entity
@Table(name = "webhook_delivery_log")
@Getter @Setter @NoArgsConstructor
public class WebhookDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id", nullable = false)
    private Webhook webhook;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** Unique delivery identifier — sent as {@code X-CBA-Delivery} header for idempotency. */
    @Column(name = "delivery_uuid", nullable = false, unique = true, length = 36)
    private String deliveryUuid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;   // JSON string of the event body

    @Column(name = "http_status")
    private Integer httpStatus;

    /** PENDING, DELIVERED, FAILED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

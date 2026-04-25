package com.cba.partner;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_webhook_deliveries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PartnerWebhookDelivery {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id", nullable = false)
    private PartnerWebhook webhook;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "delivery_uuid", nullable = false, unique = true, length = 100)
    private String deliveryUuid;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "PENDING";
    }
}

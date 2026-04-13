package com.cba.notification;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@Getter @Setter @NoArgsConstructor
public class NotificationLog {

    public enum Status { SENT, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(nullable = false, length = 80)
    private String eventType;

    private UUID recipientId;

    @Column(length = 200)
    private String recipientRef;    // masked email/phone — never stored in plaintext

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationTemplate.DeliveryMethod deliveryMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private OffsetDateTime sentAt = OffsetDateTime.now();
}

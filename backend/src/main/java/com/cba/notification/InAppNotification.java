package com.cba.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "in_app_notifications")
@Getter @Setter @NoArgsConstructor
public class InAppNotification {

    public enum Type {
        LOAN_APPROVED, LOAN_DISBURSED, LOAN_IN_ARREARS, LOAN_WRITTEN_OFF,
        ACCOUNT_OPENED, ACCOUNT_CLOSED, ACCOUNT_FROZEN,
        KYC_APPROVED, KYC_REJECTED,
        LARGE_TRANSACTION, PAYMENT_COMPLETED, PAYMENT_FAILED,
        SYSTEM_ALERT
    }

    public enum Severity { INFO, WARNING, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity = Severity.INFO;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 50)
    private String entityType;

    @Column
    private UUID entityId;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

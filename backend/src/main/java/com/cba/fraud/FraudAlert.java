package com.cba.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
@Getter @Setter
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_name", length = 100)
    private String ruleName;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 40)
    private String status = "OPEN";

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details = "{}";

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}

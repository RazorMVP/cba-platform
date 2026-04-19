package com.cba.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_cases")
@Getter @Setter
public class FraudCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_number", unique = true, nullable = false, length = 30)
    private String caseNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 40)
    private String status = "OPEN";

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel = "MEDIUM";

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

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

package com.cba.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_rules")
@Getter @Setter
public class FraudRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean blocking = false;

    @Column(nullable = false, length = 20)
    private String severity = "MEDIUM";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String params = "{}";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}

package com.cba.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_risk_scores")
@Getter @Setter
public class CustomerRiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", unique = true, nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private int score = 0;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel = "LOW";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String factors = "{}";

    @Column(name = "open_alerts_count", nullable = false)
    private int openAlertsCount = 0;

    @Column(name = "confirmed_cases_count", nullable = false)
    private int confirmedCasesCount = 0;

    @Column(name = "blacklist_hits", nullable = false)
    private int blacklistHits = 0;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt = Instant.now();

    @Version
    private Long version;
}

package com.cba.card.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_score_log")
@Getter @Setter @NoArgsConstructor
public class FraudScoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "authorization_log_id", nullable = false)
    private UUID authorizationLogId;

    @Column(name = "rule_id", nullable = false, length = 30)
    private String ruleId;

    @Column(name = "score_contribution", nullable = false)
    private int scoreContribution;

    @Column(nullable = false)
    private boolean triggered;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

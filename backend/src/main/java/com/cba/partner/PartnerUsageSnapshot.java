package com.cba.partner;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Daily per-organization API usage aggregate. Written by {@link PartnerUsageRecorder}
 * via an atomic native UPSERT (one row per org per day), read back by the usage endpoints.
 */
@Entity
@Table(name = "partner_usage_snapshots")
@Getter
@Setter
public class PartnerUsageSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_calls", nullable = false)
    private int totalCalls;

    @Column(name = "success_calls", nullable = false)
    private int successCalls;

    @Column(name = "error_calls", nullable = false)
    private int errorCalls;

    /** Endpoint label -> call count for the day (JSONB object). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_endpoints", columnDefinition = "jsonb")
    private Map<String, Integer> topEndpoints;

    @Column(name = "created_at")
    private Instant createdAt;
}

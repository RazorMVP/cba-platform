package com.cba.card.openbanking.apikey;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An API key granting M2M access to the Card API ({@code /card-api/v1/}).
 *
 * <h3>Security</h3>
 * Only the SHA-256 hex digest of the raw key is stored. The raw key is shown
 * to the caller once at creation and is never retrievable again.
 * SHA-256 (not PBKDF2) is appropriate here because the raw key is 256 random
 * bits — brute force is infeasible, and a salt adds no practical security.
 */
@Entity
@Table(name = "api_keys")
@Getter @Setter @NoArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    /** SHA-256 hex of the raw key. Never store the raw key. */
    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    private String keyHash;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Granted scopes: {@code CARD_READ}, {@code CARD_WRITE},
     * {@code WEBHOOK_MANAGE}, {@code ANALYTICS_READ}, {@code SETTLEMENT_READ}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> scopes = new ArrayList<>();

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    /** Rate limit tier: SANDBOX, BASIC, PRO, ENTERPRISE. */
    @Column(nullable = false, length = 20)
    private String tier = "BASIC";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

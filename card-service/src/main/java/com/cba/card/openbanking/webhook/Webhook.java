package com.cba.card.openbanking.webhook;

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
 * A registered webhook endpoint for Card API event delivery.
 *
 * <h3>Signing</h3>
 * Each delivery is signed: {@code X-CBA-Signature: sha256=HMAC-SHA256(payload, secret)}.
 * The {@code secret} column stores the signing secret in plaintext (protected by
 * DB-at-rest encryption in production). It is returned once at registration and
 * never again — callers must save it immediately.
 */
@Entity
@Table(name = "webhooks")
@Getter @Setter @NoArgsConstructor
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "callback_url", nullable = false)
    private String callbackUrl;

    /**
     * Event types this webhook subscribes to.
     * Values from the event catalogue: {@code AUTHORIZATION.APPROVED},
     * {@code CARD.ISSUED}, {@code FRAUD.RULE_TRIGGERED}, etc.
     * An empty list means subscribe to ALL events.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> events = new ArrayList<>();

    /**
     * Signing secret stored in the {@code secret_hash} column.
     * Despite the column name, the actual secret (not a hash) is stored here
     * to allow HMAC computation at delivery time.
     */
    @Column(name = "secret_hash", nullable = false, length = 128)
    private String secret;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

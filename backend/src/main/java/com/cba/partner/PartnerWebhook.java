package com.cba.partner;

import com.cba.common.audit.AuditableEntity;
import com.cba.common.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "partner_webhooks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PartnerWebhook extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private PartnerOrganization organization;

    @Column(nullable = false)
    private String name;

    @Column(name = "callback_url", nullable = false, columnDefinition = "TEXT")
    private String callbackUrl;

    // Encrypted at rest (Jasypt AES-256) — NOT hashed: the cleartext is the HMAC signing
    // key used when dispatching webhooks, so it must remain reversible. Column widened to
    // TEXT in V52 to hold the ciphertext.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret_hash", columnDefinition = "TEXT")
    private String secret;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> events;

    @Column(nullable = false)
    private boolean active;
}

package com.cba.partner;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "partner_api_keys")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PartnerApiKey extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private PartnerOrganization organization;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> scopes;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private boolean active;

    private java.time.Instant lastUsedAt;
}

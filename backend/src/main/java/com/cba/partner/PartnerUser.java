package com.cba.partner;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "partner_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PartnerUser extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private PartnerOrganization organization;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private boolean active;

    @Version
    private Long version;
}

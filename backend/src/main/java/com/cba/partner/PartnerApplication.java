package com.cba.partner;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "partner_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PartnerApplication extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private PartnerOrganization organization;

    private String businessType;

    @Column(columnDefinition = "TEXT")
    private String useCase;

    private String estimatedMonthlyCalls;
    private String website;
    private String technicalContact;

    @Column(columnDefinition = "TEXT")
    private String complianceNotes;

    @Column(nullable = false)
    private String status;

    private String reviewedBy;
    private java.time.Instant reviewedAt;

    @Version
    private Long version;
}

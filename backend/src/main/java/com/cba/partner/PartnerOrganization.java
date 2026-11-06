package com.cba.partner;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "partner_organizations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PartnerOrganization extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false)
    private String name;

    private String website;
    private String businessType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartnerStatus status;

    @Column(nullable = false)
    private String tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartnerEnvironment environment;

    private String applicationStatus;
    private String useCase;
    private String estimatedMonthlyCalls;
    private String technicalContact;

    @Column(columnDefinition = "TEXT")
    private String complianceNotes;

    private String approvedBy;
    private java.time.Instant approvedAt;

    /** While SUSPENDED: the status to restore on reactivation (SANDBOX, PENDING_REVIEW or PRODUCTION). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_suspension")
    private PartnerStatus statusBeforeSuspension;

    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    public boolean isSuspended() {
        return status == PartnerStatus.SUSPENDED;
    }
}

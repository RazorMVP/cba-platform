package com.cba.social;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sms_campaigns")
@Getter @Setter @NoArgsConstructor
public class SmsCampaign {

    public enum CampaignType { INDIVIDUAL, ALL, QUERY }
    public enum TriggerType  { DIRECT, SCHEDULED, TRIGGERED }
    public enum Status       { PENDING, WAITING_FOR_ACTIVATION, ACTIVE, CLOSED, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false, length = 30)
    private CampaignType campaignType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private TriggerType triggerType = TriggerType.SCHEDULED;

    @Column(name = "run_report_id")
    private UUID runReportId;

    @Column(name = "param_value", columnDefinition = "text")
    private String paramValue;

    @Column(name = "report_param_name", length = 100)
    private String reportParamName;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(length = 100)
    private String recurrence;

    @Column(name = "run_date")
    private LocalDate runDate;

    @Column(name = "next_trigger_date")
    private OffsetDateTime nextTriggerDate;

    @Column(name = "last_trigger_date")
    private OffsetDateTime lastTriggerDate;

    @Column(name = "submitted_on_date", nullable = false)
    private LocalDate submittedOnDate = LocalDate.now();

    @Column(name = "closed_on_date")
    private LocalDate closedOnDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

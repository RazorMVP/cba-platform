package com.cba.social;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "report_mailing_jobs")
@Getter @Setter @NoArgsConstructor
public class ReportMailingJob {

    public enum OutputType { CSV, PDF, XLS }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 100)
    private String recurrence;

    @Column(name = "next_run_date_time")
    private OffsetDateTime nextRunDateTime;

    @Column(name = "email_recipients", nullable = false, columnDefinition = "text")
    private String emailRecipients;

    @Column(name = "report_name", nullable = false, length = 200)
    private String reportName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_params", columnDefinition = "jsonb")
    private Map<String, String> reportParams;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_type", nullable = false, length = 20)
    private OutputType outputType = OutputType.CSV;

    @Column(name = "email_subject", length = 300)
    private String emailSubject;

    @Column(name = "email_message", columnDefinition = "text")
    private String emailMessage;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "run_count", nullable = false)
    private long runCount = 0;

    @Column(name = "previous_run_start_time")
    private OffsetDateTime previousRunStartTime;

    @Column(name = "previous_run_end_time")
    private OffsetDateTime previousRunEndTime;

    @Column(name = "previous_run_status", length = 20)
    private String previousRunStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

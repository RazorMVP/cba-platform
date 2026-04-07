package com.cba.report;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter @Setter @NoArgsConstructor
public class Report extends AuditableEntity {

    public enum Category { LOAN, SAVINGS, TELLER, CUSTOMER, ACCOUNTING, MIXED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String reportName;

    @Column(name = "report_type", length = 20)
    private String reportType = "TABLE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.MIXED;

    @Column(name = "report_sql", nullable = false, columnDefinition = "TEXT")
    private String reportSql;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "core_report", nullable = false)
    private boolean coreReport = false;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportParameter> parameters = new ArrayList<>();
}

package com.cba.report;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "report_parameters")
@Getter @Setter @NoArgsConstructor
public class ReportParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "parameter_name", nullable = false, length = 100)
    private String parameterName;

    @Column(name = "parameter_label", length = 100)
    private String parameterLabel;

    @Column(name = "parameter_type", nullable = false, length = 20)
    private String parameterType = "STRING"; // STRING, DATE, NUMBER, BOOLEAN

    @Column(name = "default_value", length = 255)
    private String defaultValue;

    @Column(nullable = false)
    private boolean required = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}

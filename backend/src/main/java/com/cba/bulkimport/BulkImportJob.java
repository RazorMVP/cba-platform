package com.cba.bulkimport;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bulk_import_jobs")
@Getter @Setter
public class BulkImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private String fileName;

    private int totalRows;
    private int successCount;
    private int failureCount;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorSummary;

    private String importedBy;

    @CreationTimestamp
    private Instant createdAt;
}

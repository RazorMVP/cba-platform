package com.cba.social;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "datatable_column_definitions")
@Getter @Setter @NoArgsConstructor
public class DataTableColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "datatable_id", nullable = false)
    private DataTable dataTable;

    @Column(name = "column_name", nullable = false, length = 100)
    private String columnName;

    @Column(name = "column_type", nullable = false, length = 30)
    private String columnType;

    @Column(name = "column_length")
    private Integer columnLength;

    @Column(name = "is_nullable")
    private boolean nullable = true;

    @Column(name = "is_unique")
    private boolean unique = false;

    @Column(name = "code_id")
    private UUID codeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

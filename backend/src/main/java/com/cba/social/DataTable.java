package com.cba.social;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "datatables")
@Getter @Setter @NoArgsConstructor
public class DataTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "registered_table_name", nullable = false, unique = true, length = 100)
    private String registeredTableName;

    @Column(name = "application_table_name", nullable = false, length = 50)
    private String applicationTableName;

    @Column(name = "allow_multiple_rows")
    private boolean allowMultipleRows = false;

    @OneToMany(mappedBy = "dataTable", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DataTableColumn> columns = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

package com.cba.system;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "global_configurations")
@Getter @Setter @NoArgsConstructor
public class GlobalConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "string_value", length = 255)
    private String stringValue;

    @Column(name = "numeric_value")
    private Long numericValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "is_enabled")
    private boolean enabled = true;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

package com.cba.accounting;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "provisioning_criteria")
@Getter @Setter @NoArgsConstructor
public class ProvisioningCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "criteria_name", nullable = false, unique = true, length = 200)
    private String criteriaName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "criteria", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProvisioningCriteriaDefinition> definitions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

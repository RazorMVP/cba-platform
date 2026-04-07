package com.cba.office;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "offices")
@Getter @Setter @NoArgsConstructor
public class Office extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String externalId;

    @Column(name = "opening_date")
    private java.time.LocalDate openingDate;

    /**
     * Materialised path hierarchy: ".parentId.thisId." for fast subtree queries.
     * Root office has hierarchy = ".id."
     */
    @Column(nullable = false, length = 255)
    private String hierarchy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Office parent;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;
}

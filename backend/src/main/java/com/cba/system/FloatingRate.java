package com.cba.system;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "floating_rates")
@Getter @Setter @NoArgsConstructor
public class FloatingRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_base_lending_rate")
    private boolean baseLendingRate = false;

    @Column(name = "is_active")
    private boolean active = true;

    @OneToMany(mappedBy = "floatingRate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FloatingRatePeriod> ratePeriods = new ArrayList<>();

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

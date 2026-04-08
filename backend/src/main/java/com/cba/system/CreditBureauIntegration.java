package com.cba.system;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_bureau_integrations")
@Getter @Setter @NoArgsConstructor
public class CreditBureauIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "impl_class", nullable = false, length = 300)
    private String implClass;

    @Column(name = "credit_bureau_id", length = 100)
    private String creditBureauId;

    @Column(length = 10)
    private String country;

    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

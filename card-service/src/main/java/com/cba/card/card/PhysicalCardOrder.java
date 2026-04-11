package com.cba.card.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "physical_card_orders")
@Getter @Setter @NoArgsConstructor
public class PhysicalCardOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(nullable = false, length = 30)
    private String status = "ORDERED";

    @Column(name = "activation_code", length = 20)
    private String activationCode;

    @Column(name = "card_bureau_ref", length = 50)
    private String cardBureauRef;

    @Column(name = "production_request_date")
    private LocalDate productionRequestDate;

    @Column(name = "dispatch_date")
    private LocalDate dispatchDate;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

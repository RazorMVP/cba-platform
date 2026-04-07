package com.cba.system;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "floating_rate_periods")
@Getter @Setter @NoArgsConstructor
public class FloatingRatePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floating_rate_id", nullable = false)
    private FloatingRate floatingRate;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "interest_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "is_differential_to_base_lending_rate")
    private boolean differentialToBaseLendingRate = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

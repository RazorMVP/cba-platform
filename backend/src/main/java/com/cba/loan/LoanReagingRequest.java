package com.cba.loan;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_reaging_requests")
@Getter @Setter @NoArgsConstructor
public class LoanReagingRequest {

    public enum Status { PENDING, APPROVED, REJECTED }
    public enum FrequencyType { DAYS, WEEKS, MONTHS }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "frequency_number")
    private Integer frequencyNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", length = 20)
    private FrequencyType frequencyType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "number_of_installments")
    private Integer numberOfInstallments;

    @Column(name = "is_preview")
    private boolean preview = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "requested_on_date", nullable = false)
    private LocalDate requestedOnDate;

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

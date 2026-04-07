package com.cba.loan;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_reschedule_requests")
@Getter @Setter @NoArgsConstructor
public class LoanRescheduleRequest {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "reschedule_from_installment")
    private Integer rescheduleFromInstallment;

    @Column(name = "new_interest_rate", precision = 19, scale = 6)
    private BigDecimal newInterestRate;

    @Column(name = "adjust_repayment_date")
    private LocalDate adjustRepaymentDate;

    @Column(name = "grace_on_principal")
    private Integer graceOnPrincipal;

    @Column(name = "grace_on_interest")
    private Integer graceOnInterest;

    @Column(name = "extra_terms")
    private Integer extraTerms;

    @Column(name = "recalculate_interest")
    private boolean recalculateInterest = true;

    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "requested_on_date", nullable = false)
    private LocalDate requestedOnDate;

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

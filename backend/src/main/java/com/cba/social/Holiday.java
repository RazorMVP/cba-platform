package com.cba.social;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "holidays")
@Getter @Setter @NoArgsConstructor
public class Holiday {

    public enum RepaymentSchedulingType { SAME_DAY, NEXT_WORKING_DAY, PREVIOUS_WORKING_DAY, NEXT_REPAYMENT_MEETING_DATE }
    public enum Status { PENDING, ACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "repayment_scheduling_type", length = 50)
    @Enumerated(EnumType.STRING)
    private RepaymentSchedulingType repaymentSchedulingType = RepaymentSchedulingType.NEXT_WORKING_DAY;

    @Column(name = "rescheduled_repayment_date")
    private LocalDate rescheduledRepaymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    private boolean processed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

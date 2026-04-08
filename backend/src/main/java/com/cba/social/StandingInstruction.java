package com.cba.social;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "standing_instructions")
@Getter @Setter @NoArgsConstructor
public class StandingInstruction {

    public enum InstructionType { FIXED, OUTSTANDING_BALANCE }
    public enum Priority        { HIGH, MEDIUM, LOW, URGENT }
    public enum Status          { ACTIVE, DISABLED, DELETED }
    public enum RecurrenceType  { PERIODIC_RECURRENCE, AS_PER_DUES }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "from_account_type", nullable = false, length = 20)
    private String fromAccountType = "SAVINGS";

    @Column(name = "to_client_id")
    private UUID toClientId;

    @Column(name = "to_account_id")
    private UUID toAccountId;

    @Column(name = "to_account_type", length = 20)
    private String toAccountType = "SAVINGS";

    @Enumerated(EnumType.STRING)
    @Column(name = "instruction_type", nullable = false, length = 20)
    private InstructionType instructionType = InstructionType.FIXED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "validity_from_date")
    private LocalDate validityFromDate;

    @Column(name = "validity_till_date")
    private LocalDate validityTillDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false, length = 20)
    private RecurrenceType recurrenceType = RecurrenceType.PERIODIC_RECURRENCE;

    @Column(name = "recurrence_frequency", nullable = false)
    private int recurrenceFrequency = 1;

    @Column(name = "recurrence_interval", nullable = false)
    private int recurrenceInterval = 1;

    @Column(name = "recurrence_on_day")
    private Integer recurrenceOnDay;

    @Column(name = "recurrence_on_nth_day", length = 10)
    private String recurrenceOnNthDay;

    @Column(name = "recurrence_on_day_of_month")
    private Integer recurrenceOnDayOfMonth;

    @Column(name = "next_run_for_date")
    private LocalDate nextRunForDate;

    @Column(name = "last_run_history")
    private OffsetDateTime lastRunHistory;

    @Column(name = "transfer_type", nullable = false, length = 30)
    private String transferType = "ACCOUNT_TRANSFER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

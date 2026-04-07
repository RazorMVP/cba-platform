package com.cba.group;

import com.cba.common.audit.AuditableEntity;
import com.cba.office.Office;
import com.cba.office.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "centers")
@Getter @Setter @NoArgsConstructor
public class Center extends AuditableEntity {

    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "external_id", unique = true, length = 50)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "activation_date")
    private LocalDate activationDate;

    @Column(name = "meeting_day_of_week", length = 10)
    private String meetingDayOfWeek;
}

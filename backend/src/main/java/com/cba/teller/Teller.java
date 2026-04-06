package com.cba.teller;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tellers")
@Getter
@Setter
@NoArgsConstructor
public class Teller extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "branch_code", nullable = false, length = 10)
    private String branchCode;

    @Column(name = "office_id", length = 50)
    private String officeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TellerStatus status = TellerStatus.INACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate = LocalDate.now();

    @Column(name = "end_date")
    private LocalDate endDate;
}

package com.cba.office;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "staff")
@Getter @Setter @NoArgsConstructor
public class Staff extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "display_name", length = 201)
    private String displayName;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "mobile_no", length = 20)
    private String mobileNo;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "is_loan_officer", nullable = false)
    private boolean loanOfficer = false;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @PrePersist @PreUpdate
    public void computeDisplayName() {
        this.displayName = firstName + " " + lastName;
    }
}

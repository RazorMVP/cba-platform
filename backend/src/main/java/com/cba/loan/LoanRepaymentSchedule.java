package com.cba.loan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_repayment_schedule")
@Getter
@Setter
@NoArgsConstructor
public class LoanRepaymentSchedule {

    public enum InstallmentStatus {
        PENDING, PAID, PARTIALLY_PAID, OVERDUE, WAIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestDue;

    @Column(name = "fees_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal feesDue = BigDecimal.ZERO;

    @Column(name = "total_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDue;

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "fees_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal feesPaid = BigDecimal.ZERO;

    @Column(name = "total_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(name = "paid_date")
    private LocalDate paidDate;
}

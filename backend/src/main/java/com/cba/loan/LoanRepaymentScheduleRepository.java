package com.cba.loan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface LoanRepaymentScheduleRepository extends JpaRepository<LoanRepaymentSchedule, UUID> {

    @Query("SELECT COUNT(s) FROM LoanRepaymentSchedule s WHERE s.dueDate >= :start AND s.dueDate <= :end")
    long countDueBetween(LocalDate start, LocalDate end);

    @Query("SELECT COUNT(s) FROM LoanRepaymentSchedule s WHERE s.dueDate >= :start AND s.dueDate <= :end AND s.status IN ('PAID', 'WAIVED')")
    long countPaidBetween(LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(s.totalDue), 0) FROM LoanRepaymentSchedule s WHERE s.dueDate >= :start AND s.dueDate <= :end")
    BigDecimal sumDueBetween(LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(s.totalPaid), 0) FROM LoanRepaymentSchedule s WHERE s.dueDate >= :start AND s.dueDate <= :end")
    BigDecimal sumCollectedBetween(LocalDate start, LocalDate end);

    /*
     * Overdue = fallen due before today and not settled (a partly paid instalment is still
     * overdue), on an open loan. Derived from the due date, like Fineract: nothing stores
     * status OVERDUE, so these queries must not filter on it (they returned nothing while
     * they did).
     */

    @Query("SELECT COUNT(s) FROM LoanRepaymentSchedule s WHERE s.dueDate < :today "
            + "AND s.status NOT IN ('PAID', 'WAIVED') AND s.loan.status IN ('ACTIVE', 'IN_ARREARS')")
    long countOverdue(LocalDate today);

    @Query("SELECT COALESCE(SUM(s.totalDue - s.totalPaid), 0) FROM LoanRepaymentSchedule s WHERE s.dueDate < :today "
            + "AND s.status NOT IN ('PAID', 'WAIVED') AND s.loan.status IN ('ACTIVE', 'IN_ARREARS')")
    BigDecimal sumOverdueBalance(LocalDate today);
}

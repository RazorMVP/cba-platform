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

    @Query("SELECT COUNT(s) FROM LoanRepaymentSchedule s WHERE s.dueDate >= :start AND s.dueDate <= :end AND s.status = 'PAID'")
    long countPaidBetween(LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(s.totalDue), 0) FROM LoanRepaymentSchedule s WHERE s.dueDate >= :start AND s.dueDate <= :end")
    BigDecimal sumDueBetween(LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(s.totalPaid), 0) FROM LoanRepaymentSchedule s WHERE s.dueDate >= :start AND s.dueDate <= :end")
    BigDecimal sumCollectedBetween(LocalDate start, LocalDate end);

    @Query("SELECT COUNT(s) FROM LoanRepaymentSchedule s WHERE s.status = 'OVERDUE'")
    long countOverdue();

    @Query("SELECT COALESCE(SUM(s.totalDue - s.totalPaid), 0) FROM LoanRepaymentSchedule s WHERE s.status = 'OVERDUE'")
    BigDecimal sumOverdueBalance();
}

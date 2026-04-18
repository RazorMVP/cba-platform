package com.cba.loan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {

    Page<Loan> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Loan> findByStatus(LoanStatus status, Pageable pageable);

    Page<Loan> findByStatusIn(List<LoanStatus> statuses, Pageable pageable);

    @Query("SELECT l FROM Loan l WHERE l.status = 'ACTIVE' " +
           "AND EXISTS (SELECT s FROM LoanRepaymentSchedule s " +
           "WHERE s.loan = l AND s.dueDate < :today AND s.status = 'PENDING')")
    List<Loan> findLoansWithOverdueInstallments(LocalDate today);

    long countByStatus(LoanStatus status);

    @Query("SELECT COUNT(DISTINCT s.loan.id) FROM LoanRepaymentSchedule s " +
           "WHERE s.status = 'OVERDUE' AND s.dueDate >= :from AND s.dueDate < :to")
    long countLoansWithOverdueBetween(LocalDate from, LocalDate to);

    @Query("SELECT COUNT(DISTINCT s.loan.id) FROM LoanRepaymentSchedule s " +
           "WHERE s.status = 'OVERDUE' AND s.dueDate < :before")
    long countLoansWithOverdueBefore(LocalDate before);
}

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

    /** Ids only, so a CoB job can process each loan in its own transaction. */
    @Query("SELECT l.id FROM Loan l WHERE l.status IN :statuses ORDER BY l.id")
    List<UUID> findIdsByStatusIn(java.util.Collection<LoanStatus> statuses);

    /** Per currency and IFRS 9 stage: [currency, stage, loans, principal, loss allowance, prudential provision]. */
    @Query("SELECT l.product.currencyCode, l.eclStage, COUNT(l), SUM(l.outstandingBalance), " +
           "SUM(l.lossAllowance), SUM(l.prudentialProvision) FROM Loan l WHERE l.status IN :statuses " +
           "GROUP BY l.product.currencyCode, l.eclStage ORDER BY l.product.currencyCode, l.eclStage")
    List<Object[]> provisionTotals(java.util.Collection<LoanStatus> statuses);

    long countByStatus(LoanStatus status);

    /**
     * Loans in arrears whose oldest unsettled instalment fell due in [from, to): one bucket
     * per loan, by {@code overdueSince} as the arrears run keeps it (with days past due).
     */
    @Query("SELECT COUNT(l) FROM Loan l WHERE l.status = 'IN_ARREARS' " +
           "AND l.overdueSince >= :from AND l.overdueSince < :to")
    long countInArrearsOverdueSinceBetween(LocalDate from, LocalDate to);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.status = 'IN_ARREARS' AND l.overdueSince < :before")
    long countInArrearsOverdueSinceBefore(LocalDate before);
}

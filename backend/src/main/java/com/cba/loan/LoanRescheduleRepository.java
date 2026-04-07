package com.cba.loan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface LoanRescheduleRepository extends JpaRepository<LoanRescheduleRequest, UUID> {
    Page<LoanRescheduleRequest> findByLoanId(UUID loanId, Pageable pageable);
}

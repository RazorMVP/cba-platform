package com.cba.loan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface LoanReagingRepository extends JpaRepository<LoanReagingRequest, UUID> {
    Page<LoanReagingRequest> findByLoanId(UUID loanId, Pageable pageable);
}

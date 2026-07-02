package com.cba.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreditBureauProductMappingRepository extends JpaRepository<CreditBureauProductMapping, UUID> {
    List<CreditBureauProductMapping> findByCreditBureauId(UUID creditBureauId);

    List<CreditBureauProductMapping> findByLoanProductId(UUID loanProductId);
}

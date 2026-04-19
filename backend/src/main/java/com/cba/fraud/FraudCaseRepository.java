package com.cba.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID> {

    Optional<FraudCase> findByCaseNumber(String caseNumber);

    Page<FraudCase> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<FraudCase> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    @Query("SELECT c FROM FraudCase c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:riskLevel IS NULL OR c.riskLevel = :riskLevel) AND " +
           "(:customerId IS NULL OR c.customerId = :customerId) " +
           "ORDER BY c.createdAt DESC")
    Page<FraudCase> findFiltered(String status, String riskLevel, UUID customerId, Pageable pageable);

    long countByStatus(String status);
}

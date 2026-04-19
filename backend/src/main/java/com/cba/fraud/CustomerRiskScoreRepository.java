package com.cba.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRiskScoreRepository extends JpaRepository<CustomerRiskScore, UUID> {

    Optional<CustomerRiskScore> findByCustomerId(UUID customerId);

    Page<CustomerRiskScore> findByRiskLevelOrderByScoreDesc(String riskLevel, Pageable pageable);

    Page<CustomerRiskScore> findByScoreGreaterThanEqualOrderByScoreDesc(int minScore, Pageable pageable);
}

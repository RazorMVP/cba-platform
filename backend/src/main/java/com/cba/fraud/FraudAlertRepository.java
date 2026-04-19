package com.cba.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    Page<FraudAlert> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<FraudAlert> findBySeverityOrderByCreatedAtDesc(String severity, Pageable pageable);

    Page<FraudAlert> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    @Query("SELECT a FROM FraudAlert a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:customerId IS NULL OR a.customerId = :customerId) " +
           "ORDER BY a.createdAt DESC")
    Page<FraudAlert> findFiltered(String status, String severity, UUID customerId, Pageable pageable);

    long countByCustomerIdAndStatus(UUID customerId, String status);

    List<FraudAlert> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, String status);
}

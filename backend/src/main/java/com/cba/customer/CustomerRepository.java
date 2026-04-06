package com.cba.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByExternalId(String externalId);

    Page<Customer> findByKycStatus(KycStatus kycStatus, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.kycStatus = :status ORDER BY c.createdAt DESC")
    Page<Customer> findByKycStatusOrderByCreatedAt(KycStatus status, Pageable pageable);
}

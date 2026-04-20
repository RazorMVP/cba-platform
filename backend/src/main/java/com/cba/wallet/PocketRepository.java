package com.cba.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PocketRepository extends JpaRepository<Pocket, UUID> {

    @Query("SELECT p FROM Pocket p WHERE p.customerId = :customerId AND p.status = 'ACTIVE' ORDER BY p.createdAt DESC")
    List<Pocket> findActiveByCustomerId(UUID customerId);
}

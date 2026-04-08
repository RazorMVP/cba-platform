package com.cba.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientImageRepository extends JpaRepository<ClientImage, UUID> {
    Optional<ClientImage> findByCustomerId(UUID customerId);
}

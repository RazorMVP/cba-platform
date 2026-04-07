package com.cba.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ClientIdentifierRepository extends JpaRepository<ClientIdentifier, UUID> {
    Page<ClientIdentifier> findByCustomerId(UUID customerId, Pageable pageable);
}

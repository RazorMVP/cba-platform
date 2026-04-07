package com.cba.charge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ClientChargeRepository extends JpaRepository<ClientCharge, UUID> {
    Page<ClientCharge> findByCustomerId(UUID customerId, Pageable pageable);
}

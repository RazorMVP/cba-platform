package com.cba.teller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CashierRepository extends JpaRepository<Cashier, UUID> {
    List<Cashier> findByTellerId(UUID tellerId);
    List<Cashier> findByTellerIdAndActiveTrue(UUID tellerId);
}

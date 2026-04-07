package com.cba.charge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ChargeRepository extends JpaRepository<ChargeDefinition, UUID> {
    Page<ChargeDefinition> findByChargeAppliesTo(ChargeDefinition.ChargeAppliesTo appliesTo, Pageable pageable);
    boolean existsByName(String name);
}

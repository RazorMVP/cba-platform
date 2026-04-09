package com.cba.charge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChargeDefinitionRepository extends JpaRepository<ChargeDefinition, UUID> {
    List<ChargeDefinition> findByActiveTrue();
    List<ChargeDefinition> findByChargeAppliesTo(ChargeDefinition.ChargeAppliesTo appliesTo);
}

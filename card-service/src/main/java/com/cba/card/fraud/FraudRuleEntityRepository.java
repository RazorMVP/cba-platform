package com.cba.card.fraud;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudRuleEntityRepository extends JpaRepository<FraudRuleEntity, UUID> {

    List<FraudRuleEntity> findByEnabledTrue();

    Optional<FraudRuleEntity> findByRuleId(String ruleId);
}

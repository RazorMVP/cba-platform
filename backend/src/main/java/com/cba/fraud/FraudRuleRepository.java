package com.cba.fraud;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {
    List<FraudRule> findByEnabledTrueOrderByNameAsc();
    List<FraudRule> findByEnabledTrueAndBlockingTrueOrderByNameAsc();
    List<FraudRule> findByRuleTypeOrderByNameAsc(String ruleType);
}

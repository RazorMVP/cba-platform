package com.cba.card.fraud;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudScoreLogRepository extends JpaRepository<FraudScoreLog, UUID> {

    List<FraudScoreLog> findByAuthorizationLogId(UUID authorizationLogId);
}

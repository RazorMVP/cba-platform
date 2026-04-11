package com.cba.card.interchange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterchangeLogRepository extends JpaRepository<InterchangeLog, UUID> {

    Optional<InterchangeLog> findTopByAuthorizationLogIdOrderByCalculatedAtDesc(UUID authorizationLogId);

    List<InterchangeLog> findBySchemeOrderByCalculatedAtDesc(String scheme);
}

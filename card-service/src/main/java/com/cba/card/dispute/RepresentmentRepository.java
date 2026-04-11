package com.cba.card.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepresentmentRepository extends JpaRepository<Representment, UUID> {

    Optional<Representment> findTopByDisputeIdOrderByCreatedAtDesc(UUID disputeId);

    /** Used by the timeframe enforcer to find representments whose issuer deadline has passed. */
    List<Representment> findByStatusAndDeadlineBefore(String status, LocalDate date);
}

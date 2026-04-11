package com.cba.card.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetrievalRequestRepository extends JpaRepository<RetrievalRequest, UUID> {

    Optional<RetrievalRequest> findTopByDisputeIdOrderByCreatedAtDesc(UUID disputeId);

    /** Used by the timeframe enforcer to find expired unanswered requests. */
    List<RetrievalRequest> findByStatusAndDeadlineBefore(String status, LocalDate date);
}

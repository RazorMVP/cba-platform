package com.cba.card.dispute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CardDisputeRepository extends JpaRepository<CardDispute, UUID> {
    List<CardDispute> findByCardIdOrderByCreatedAtDesc(UUID cardId);
    List<CardDispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status);
    List<CardDispute> findByRaisedByOrderByCreatedAtDesc(UUID customerId);
}

package com.cba.card.bureau;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BureauJobItemRepository extends JpaRepository<BureauJobItem, UUID> {

    List<BureauJobItem> findByJobId(UUID jobId);

    List<BureauJobItem> findByJobIdAndStatus(UUID jobId, BureauJobItemStatus status);

    boolean existsByCardIdAndStatusIn(UUID cardId, List<BureauJobItemStatus> statuses);
}

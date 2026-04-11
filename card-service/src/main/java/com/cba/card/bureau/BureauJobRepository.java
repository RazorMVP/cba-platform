package com.cba.card.bureau;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BureauJobRepository extends JpaRepository<BureauJob, UUID> {

    List<BureauJob> findAllByOrderByCreatedAtDesc();

    List<BureauJob> findByStatusOrderByCreatedAtDesc(BureauJobStatus status);

    Optional<BureauJob> findByBatchRef(String batchRef);
}

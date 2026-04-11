package com.cba.card.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementTransmissionRepository extends JpaRepository<SettlementTransmission, UUID> {

    List<SettlementTransmission> findByBatchIdOrderByCreatedAtDesc(UUID batchId);

    List<SettlementTransmission> findBySettlementDateOrderBySchemeAsc(LocalDate settlementDate);

    List<SettlementTransmission> findByStatusOrderByCreatedAtDesc(String status);

    /** Idempotency check — has this batch+scheme already been successfully transmitted? */
    Optional<SettlementTransmission> findByBatchIdAndSchemeAndStatus(
            UUID batchId, String scheme, String status);
}

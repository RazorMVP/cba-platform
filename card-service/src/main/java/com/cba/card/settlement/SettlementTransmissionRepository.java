package com.cba.card.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SettlementTransmissionRepository extends JpaRepository<SettlementTransmission, UUID> {

    List<SettlementTransmission> findByBatchIdOrderByCreatedAtDesc(UUID batchId);

    List<SettlementTransmission> findBySettlementDateOrderBySchemeAsc(LocalDate settlementDate);

    List<SettlementTransmission> findByStatusOrderByCreatedAtDesc(String status);

    /** Has this batch+scheme ever been attempted? The nightly run only makes first attempts. */
    boolean existsByBatchIdAndScheme(UUID batchId, String scheme);

    /** PENDING transmissions whose next attempt is due (the retry poller). */
    List<SettlementTransmission> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            String status, OffsetDateTime now);

    /** SENDING transmissions whose attempt started before the cutoff — interrupted mid-send. */
    List<SettlementTransmission> findByStatusAndLastAttemptAtBefore(String status, OffsetDateTime cutoff);
}

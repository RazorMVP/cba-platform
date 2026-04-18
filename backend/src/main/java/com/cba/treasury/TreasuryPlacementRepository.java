package com.cba.treasury;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreasuryPlacementRepository extends JpaRepository<TreasuryPlacement, UUID> {

    Optional<TreasuryPlacement> findByReference(String reference);

    List<TreasuryPlacement> findByStatus(TreasuryPlacement.Status status);

    List<TreasuryPlacement> findByMaturityDateBeforeAndStatus(LocalDate date, TreasuryPlacement.Status status);
}

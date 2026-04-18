package com.cba.treasury;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreasuryInterbankPositionRepository extends JpaRepository<TreasuryInterbankPosition, UUID> {

    Optional<TreasuryInterbankPosition> findByReference(String reference);

    List<TreasuryInterbankPosition> findByStatus(TreasuryInterbankPosition.Status status);

    List<TreasuryInterbankPosition> findByDirection(TreasuryInterbankPosition.Direction direction);
}

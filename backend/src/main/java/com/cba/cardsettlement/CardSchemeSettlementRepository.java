package com.cba.cardsettlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardSchemeSettlementRepository extends JpaRepository<CardSchemeSettlement, UUID> {
    Optional<CardSchemeSettlement> findByReference(String reference);
}

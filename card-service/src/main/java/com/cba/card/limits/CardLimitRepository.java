package com.cba.card.limits;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardLimitRepository extends JpaRepository<CardLimit, UUID> {

    Optional<CardLimit> findByCardId(UUID cardId);
}

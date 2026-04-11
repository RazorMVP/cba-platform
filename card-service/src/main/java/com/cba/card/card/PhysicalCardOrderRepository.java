package com.cba.card.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhysicalCardOrderRepository extends JpaRepository<PhysicalCardOrder, UUID> {

    List<PhysicalCardOrder> findByCardId(UUID cardId);

    Optional<PhysicalCardOrder> findByCardIdAndStatus(UUID cardId, String status);

    List<PhysicalCardOrder> findByStatus(String status);
}

package com.cba.card.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    Optional<Card> findByPanHash(String panHash);

    List<Card> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Card> findByCustomerIdAndCardType(UUID customerId, CardType cardType);

    List<Card> findByStatus(CardStatus status);

    @Query("SELECT c FROM Card c WHERE c.panPrefix = :prefix AND c.status = 'ACTIVE'")
    List<Card> findActiveByBinPrefix(@Param("prefix") String prefix);

    boolean existsByPanHash(String panHash);
}

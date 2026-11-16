package com.cba.card.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardProductRepository extends JpaRepository<CardProduct, UUID> {

    List<CardProduct> findByActiveTrue();

    List<CardProduct> findByCardType(CardType cardType);

    /** The product of a card — the card's own {@code product} is lazy, and wallet loads run outside a session. */
    @org.springframework.data.jpa.repository.Query("SELECT c.product FROM Card c WHERE c.id = :cardId")
    java.util.Optional<CardProduct> findByCardId(UUID cardId);
}

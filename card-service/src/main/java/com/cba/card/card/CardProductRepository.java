package com.cba.card.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardProductRepository extends JpaRepository<CardProduct, UUID> {

    List<CardProduct> findByActiveTrue();

    List<CardProduct> findByCardType(CardType cardType);
}

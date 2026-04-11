package com.cba.card.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrepaidWalletRepository extends JpaRepository<PrepaidWallet, UUID> {

    Optional<PrepaidWallet> findByCardId(UUID cardId);

    Optional<PrepaidWallet> findByCustomerId(UUID customerId);
}

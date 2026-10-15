package com.cba.card.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrepaidWalletRepository extends JpaRepository<PrepaidWallet, UUID> {

    Optional<PrepaidWallet> findByCardId(UUID cardId);

    /** The wallet under a row lock (SELECT … FOR UPDATE): approvals and reversals change its balance. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT w FROM PrepaidWallet w WHERE w.card.id = :cardId")
    Optional<PrepaidWallet> findByCardIdForUpdate(UUID cardId);

    Optional<PrepaidWallet> findByCustomerId(UUID customerId);
}

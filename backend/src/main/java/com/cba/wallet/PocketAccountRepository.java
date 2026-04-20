package com.cba.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PocketAccountRepository extends JpaRepository<PocketAccount, UUID> {

    Optional<PocketAccount> findByAccountId(UUID accountId);

    boolean existsByAccountId(UUID accountId);
}

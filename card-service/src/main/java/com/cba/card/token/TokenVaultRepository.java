package com.cba.card.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TokenVaultRepository extends JpaRepository<TokenVault, UUID> {

    Optional<TokenVault> findByDpanHash(String dpanHash);

    Optional<TokenVault> findByTokenRef(String tokenRef);

    List<TokenVault> findByCardIdAndStatus(UUID cardId, String status);

    List<TokenVault> findByPanHashAndStatus(String panHash, String status);
}

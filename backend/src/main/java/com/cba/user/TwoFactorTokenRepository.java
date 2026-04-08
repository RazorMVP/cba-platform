package com.cba.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TwoFactorTokenRepository extends JpaRepository<TwoFactorToken, UUID> {
    Optional<TwoFactorToken> findByTokenAndVerifiedFalse(String token);
    List<TwoFactorToken> findByUserId(UUID userId);
}

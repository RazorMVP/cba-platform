package com.cba.card.threeds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ThreeDsOtpTokenRepository extends JpaRepository<ThreeDsOtpToken, UUID> {

    /**
     * Find the most recent unverified, non-expired token for a session.
     * The service validates expiry and verified flag after retrieval.
     */
    Optional<ThreeDsOtpToken> findTopBySessionIdAndVerifiedFalseOrderByCreatedAtDesc(UUID sessionId);
}

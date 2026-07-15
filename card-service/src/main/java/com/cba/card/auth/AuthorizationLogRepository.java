package com.cba.card.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuthorizationLogRepository extends JpaRepository<AuthorizationLog, UUID> {

    List<AuthorizationLog> findByCardIdOrderByCreatedAtDesc(UUID cardId);

    /** Velocity rule: count approved transactions for a card within a time window. */
    @Query("""
           SELECT COUNT(a) FROM AuthorizationLog a
           WHERE a.cardId = :cardId
             AND a.responseCode = '00'
             AND a.createdAt >= :since
           """)
    long countApprovedSince(@Param("cardId") UUID cardId, @Param("since") OffsetDateTime since);

    /** Duplicate detection: same amount + merchant within window. */
    @Query("""
           SELECT COUNT(a) > 0 FROM AuthorizationLog a
           WHERE a.cardId     = :cardId
             AND a.amount     = :amount
             AND a.merchantId = :merchantId
             AND a.createdAt  >= :since
             AND a.responseCode = '00'
           """)
    boolean existsDuplicate(@Param("cardId") UUID cardId,
                            @Param("amount") BigDecimal amount,
                            @Param("merchantId") String merchantId,
                            @Param("since") OffsetDateTime since);

    /** Unmatched authorizations older than cutoff (for expiry CoB job). */
    @Query("""
           SELECT a FROM AuthorizationLog a
           WHERE a.responseCode = '00'
             AND a.mti IN ('0100', '0110')
             AND a.createdAt < :cutoff
           """)
    List<AuthorizationLog> findUnmatchedAuthsOlderThan(@Param("cutoff") OffsetDateTime cutoff);

    /** Idempotency guard for reversals — has a 0400 already been recorded for this (card, STAN)? */
    boolean existsByCardIdAndStanAndMti(UUID cardId, String stan, String mti);

    /** The original (non-reversal) authorization for a (card, STAN), most recent first. */
    java.util.Optional<AuthorizationLog> findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(
            UUID cardId, String stan, String mti);
}

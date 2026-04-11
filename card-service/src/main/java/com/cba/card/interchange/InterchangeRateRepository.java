package com.cba.card.interchange;

import com.cba.card.card.CardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InterchangeRateRepository extends JpaRepository<InterchangeRate, UUID> {

    /**
     * Find the best-matching interchange rate for a transaction.
     *
     * <p>Rows with an explicit MCC ({@code mccCategory} = :mcc) rank above
     * catch-all rows ({@code mccCategory IS NULL}).  Pass {@code null} for
     * {@code mcc} to retrieve only catch-all rows.
     *
     * <p>Ordered: specific MCC first, then by most-recently created.
     * The engine always takes {@code result.get(0)} when non-empty.
     */
    @Query("""
           SELECT r FROM InterchangeRate r
           WHERE r.active = true
             AND r.scheme        = :scheme
             AND r.cardType      = :cardType
             AND r.transactionType = :txnType
             AND r.channel       = :channel
             AND (:mcc IS NULL   OR r.mccCategory = :mcc OR r.mccCategory IS NULL)
             AND r.effectiveFrom <= :today
             AND (r.effectiveTo IS NULL OR r.effectiveTo >= :today)
           ORDER BY
             CASE WHEN r.mccCategory IS NULL THEN 1 ELSE 0 END ASC,
             r.createdAt DESC
           """)
    List<InterchangeRate> findBestMatch(
            @Param("scheme")   String scheme,
            @Param("cardType") CardType cardType,
            @Param("txnType")  TransactionType txnType,
            @Param("channel")  ChannelType channel,
            @Param("mcc")      String mcc,
            @Param("today")    LocalDate today);

    List<InterchangeRate> findAllByActiveTrue();
}

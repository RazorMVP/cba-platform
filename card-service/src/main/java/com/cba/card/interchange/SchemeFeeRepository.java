package com.cba.card.interchange;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SchemeFeeRepository extends JpaRepository<SchemeFee, UUID> {

    /**
     * All active scheme fees for a given scheme, effective today.
     * Multiple fee types may be returned (ASSESSMENT + NETWORK + CROSS_BORDER, etc.).
     */
    @Query("""
           SELECT f FROM SchemeFee f
           WHERE f.active = true
             AND f.scheme = :scheme
             AND f.effectiveFrom <= :today
             AND (f.effectiveTo IS NULL OR f.effectiveTo >= :today)
           ORDER BY f.feeType ASC
           """)
    List<SchemeFee> findActiveByScheme(
            @Param("scheme") String scheme,
            @Param("today")  LocalDate today);

    List<SchemeFee> findAllByActiveTrue();
}

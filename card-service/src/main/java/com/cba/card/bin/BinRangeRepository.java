package com.cba.card.bin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BinRangeRepository extends JpaRepository<BinRange, UUID> {

    /**
     * BIN range scan: find the most specific (longest) matching range.
     * Ordered by bin_start DESC to prefer 8-digit matches over 6-digit.
     */
    @Query("""
           SELECT b FROM BinRange b
           WHERE b.active = true
             AND b.binStart <= :pan8
             AND b.binEnd   >= :pan8
           ORDER BY LENGTH(b.binStart) DESC, b.binStart DESC
           """)
    List<BinRange> findByPan8(@Param("pan8") String pan8);

    List<BinRange> findBySchemeAndActiveTrue(SchemeType scheme);

    List<BinRange> findAllByActiveTrue();
}

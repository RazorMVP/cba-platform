package com.cba.treasury;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiquiditySnapshotRepository extends JpaRepository<LiquiditySnapshot, UUID> {
    List<LiquiditySnapshot> findByCurrencyCodeOrderBySnapshotDateDesc(String currencyCode);
    List<LiquiditySnapshot> findBySnapshotDateOrderByCurrencyCode(LocalDate date);
    Optional<LiquiditySnapshot> findBySnapshotDateAndCurrencyCode(LocalDate date, String currencyCode);
    List<LiquiditySnapshot> findByCurrencyCodeAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            String currencyCode, LocalDate from, LocalDate to);
}

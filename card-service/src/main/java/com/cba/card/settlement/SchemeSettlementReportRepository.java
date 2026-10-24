package com.cba.card.settlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchemeSettlementReportRepository extends JpaRepository<SchemeSettlementReport, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SchemeSettlementReport r WHERE r.scheme = :scheme AND r.settlementDate = :settlementDate "
            + "AND r.currencyCode = :currencyCode")
    Optional<SchemeSettlementReport> findForUpdate(String scheme, LocalDate settlementDate, String currencyCode);

    List<SchemeSettlementReport> findBySettlementDateOrderBySchemeAscCurrencyCodeAsc(LocalDate settlementDate);
}

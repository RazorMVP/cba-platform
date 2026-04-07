package com.cba.accounting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    List<JournalEntry> findByEntityTypeAndEntityId(
            JournalEntry.EntityType entityType, UUID entityId);

    Page<JournalEntry> findByTransactionDateBetween(
            LocalDate from, LocalDate to, Pageable pageable);

    Page<JournalEntry> findByGlAccountIdAndTransactionDateBetween(
            UUID glAccountId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("""
            SELECT j FROM JournalEntry j
            WHERE j.glAccount.id = :glAccountId
              AND j.transactionDate BETWEEN :from AND :to
              AND j.reversed = false
            """)
    List<JournalEntry> findUnreversedByAccountAndDateRange(
            @Param("glAccountId") UUID glAccountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}

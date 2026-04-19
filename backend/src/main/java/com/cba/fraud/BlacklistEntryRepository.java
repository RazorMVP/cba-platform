package com.cba.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BlacklistEntryRepository extends JpaRepository<BlacklistEntry, UUID> {

    @Query("SELECT e FROM BlacklistEntry e WHERE e.active = true AND " +
           "(e.expiresAt IS NULL OR e.expiresAt > :now) AND " +
           "UPPER(e.entityValue) LIKE UPPER(CONCAT('%', :query, '%'))")
    List<BlacklistEntry> searchActive(String query, Instant now);

    @Query("SELECT e FROM BlacklistEntry e WHERE e.active = true AND " +
           "(e.expiresAt IS NULL OR e.expiresAt > :now) AND " +
           "e.entityType = :entityType AND UPPER(e.entityValue) = UPPER(:entityValue)")
    List<BlacklistEntry> findActiveByTypeAndValue(String entityType, String entityValue, Instant now);

    Page<BlacklistEntry> findByActiveOrderByCreatedAtDesc(boolean active, Pageable pageable);

    @Query("SELECT e FROM BlacklistEntry e WHERE " +
           "(:entityType IS NULL OR e.entityType = :entityType) AND " +
           "(:active IS NULL OR e.active = :active) " +
           "ORDER BY e.createdAt DESC")
    Page<BlacklistEntry> findFiltered(String entityType, Boolean active, Pageable pageable);
}

package com.cba.teller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TellerSessionRepository extends JpaRepository<TellerSession, UUID> {
    List<TellerSession> findByTellerId(UUID tellerId);
    List<TellerSession> findByCashierId(UUID cashierId);
    Optional<TellerSession> findByCashierIdAndSessionDate(UUID cashierId, LocalDate sessionDate);
    List<TellerSession> findByStatus(SessionStatus status);
}

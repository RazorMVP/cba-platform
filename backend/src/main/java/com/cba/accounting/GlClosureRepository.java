package com.cba.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GlClosureRepository extends JpaRepository<GlClosure, UUID> {
    List<GlClosure> findByOfficeIdOrderByClosingDateDesc(UUID officeId);
    Optional<GlClosure> findByOfficeIdAndClosingDate(UUID officeId, LocalDate closingDate);

    boolean existsByOfficeIdAndClosingDateGreaterThanEqual(UUID officeId, LocalDate date);
}

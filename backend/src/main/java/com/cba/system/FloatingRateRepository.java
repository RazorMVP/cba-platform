package com.cba.system;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FloatingRateRepository extends JpaRepository<FloatingRate, UUID> {
    Page<FloatingRate> findByActiveTrue(Pageable pageable);
    Optional<FloatingRate> findByBaseLendingRateTrue();
    boolean existsByName(String name);
}

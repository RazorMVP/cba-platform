package com.cba.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface MakerCheckerRepository extends JpaRepository<MakerChecker, UUID> {
    Page<MakerChecker> findByStatus(MakerChecker.Status status, Pageable pageable);
}

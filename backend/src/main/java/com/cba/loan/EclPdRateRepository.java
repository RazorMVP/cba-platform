package com.cba.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EclPdRateRepository extends JpaRepository<EclPdRate, UUID> {

    List<EclPdRate> findByStageOrderByMinDpdAsc(int stage);

    List<EclPdRate> findAllByOrderByStageAscMinDpdAsc();
}

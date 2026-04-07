package com.cba.cob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CobJobHistoryRepository extends JpaRepository<CobJobHistory, UUID> {
    List<CobJobHistory> findByJobNameOrderByBusinessDateDesc(String jobName);
    Optional<CobJobHistory> findByJobNameAndBusinessDate(String jobName, LocalDate businessDate);
    List<CobJobHistory> findTop10ByOrderByStartedAtDesc();
}

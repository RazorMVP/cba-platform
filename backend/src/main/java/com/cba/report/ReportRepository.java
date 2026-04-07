package com.cba.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    Optional<Report> findByReportName(String reportName);
    List<Report> findByEnabledTrue();
    List<Report> findByCategoryAndEnabledTrue(Report.Category category);
}

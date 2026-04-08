package com.cba.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ReportMailingJobRepository extends JpaRepository<ReportMailingJob, UUID> {}

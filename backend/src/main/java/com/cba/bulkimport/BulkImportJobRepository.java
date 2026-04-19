package com.cba.bulkimport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BulkImportJobRepository extends JpaRepository<BulkImportJob, UUID> {
    List<BulkImportJob> findTop20ByOrderByCreatedAtDesc();
    List<BulkImportJob> findTop20ByEntityTypeOrderByCreatedAtDesc(String entityType);
}

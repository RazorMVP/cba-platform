package com.cba.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataTableRepository extends JpaRepository<DataTable, UUID> {
    Page<DataTable> findByApplicationTableName(String applicationTableName, Pageable pageable);
    Optional<DataTable> findByRegisteredTableName(String registeredTableName);
    boolean existsByRegisteredTableName(String registeredTableName);
}

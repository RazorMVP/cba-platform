package com.cba.system;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldConfigurationRepository extends JpaRepository<FieldConfiguration, UUID> {
    List<FieldConfiguration> findByEntityTypeOrderByDisplayOrderAsc(String entityType);
    Optional<FieldConfiguration> findByEntityTypeAndFieldName(String entityType, String fieldName);
    boolean existsByEntityTypeAndFieldName(String entityType, String fieldName);
}

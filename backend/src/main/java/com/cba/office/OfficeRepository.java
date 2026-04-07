package com.cba.office;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficeRepository extends JpaRepository<Office, UUID> {
    List<Office> findByActiveTrue();
    Optional<Office> findByExternalId(String externalId);

    @Query("SELECT o FROM Office o WHERE o.hierarchy LIKE :prefix% AND o.active = true")
    List<Office> findSubtree(@Param("prefix") String hierarchyPrefix);
}

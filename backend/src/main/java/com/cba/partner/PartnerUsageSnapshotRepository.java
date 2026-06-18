package com.cba.partner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PartnerUsageSnapshotRepository extends JpaRepository<PartnerUsageSnapshot, UUID> {

    List<PartnerUsageSnapshot> findByOrganizationIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            UUID organizationId, LocalDate from);

    List<PartnerUsageSnapshot> findBySnapshotDateGreaterThanEqual(LocalDate from);
}

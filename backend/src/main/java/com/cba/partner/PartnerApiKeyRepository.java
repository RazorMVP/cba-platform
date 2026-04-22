package com.cba.partner;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerApiKeyRepository extends JpaRepository<PartnerApiKey, UUID> {
    List<PartnerApiKey> findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(UUID orgId);
    Optional<PartnerApiKey> findByKeyHashAndActiveTrue(String keyHash);
}

package com.cba.partner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerApiKeyRepository extends JpaRepository<PartnerApiKey, UUID> {
    List<PartnerApiKey> findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(UUID orgId);

    /**
     * Fetches the organisation with the key: {@code PartnerApiKeyAuthFilter} runs as a servlet filter
     * with no open session, so a lazy {@code organization} threw on {@code getEnvironment()} — the
     * filter swallowed it and no API key ever authenticated outside dev bypass (cont. 43).
     */
    @Query("SELECT k FROM PartnerApiKey k JOIN FETCH k.organization WHERE k.keyHash = :keyHash AND k.active = true")
    Optional<PartnerApiKey> findByKeyHashAndActiveTrue(@Param("keyHash") String keyHash);
}

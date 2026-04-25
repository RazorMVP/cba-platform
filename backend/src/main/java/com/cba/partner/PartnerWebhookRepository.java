package com.cba.partner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartnerWebhookRepository extends JpaRepository<PartnerWebhook, UUID> {
    List<PartnerWebhook> findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(UUID orgId);
    List<PartnerWebhook> findByOrganizationIdOrderByCreatedAtDesc(UUID orgId);
}

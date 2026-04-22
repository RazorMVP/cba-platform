package com.cba.partner;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PartnerOrganizationRepository extends JpaRepository<PartnerOrganization, UUID> {
}

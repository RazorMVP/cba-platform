package com.cba.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ProvisioningCriteriaRepository extends JpaRepository<ProvisioningCriteria, UUID> {}

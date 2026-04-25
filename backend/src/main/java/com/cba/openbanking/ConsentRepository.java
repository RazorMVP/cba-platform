package com.cba.openbanking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<OpenBankingConsent, UUID> {
    Optional<OpenBankingConsent> findByConsentId(String consentId);
    List<OpenBankingConsent> findByTppClientIdOrderByCreatedAtDesc(String tppClientId);
}

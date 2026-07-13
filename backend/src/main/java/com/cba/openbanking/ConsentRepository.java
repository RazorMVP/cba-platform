package com.cba.openbanking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<OpenBankingConsent, UUID> {
    Optional<OpenBankingConsent> findByConsentId(String consentId);
    List<OpenBankingConsent> findByTppClientIdOrderByCreatedAtDesc(String tppClientId);

    /**
     * Consents in a non-terminal status whose expiry has passed — the candidates for the
     * expiry job. A null {@code expiry_date} is never {@code < cutoff}, so open-ended
     * consents are correctly excluded.
     */
    List<OpenBankingConsent> findByStatusInAndExpiryDateBefore(
            Collection<ConsentStatus> statuses, Instant cutoff);
}

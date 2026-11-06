package com.cba.openbanking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<OpenBankingConsent, UUID>,
        JpaSpecificationExecutor<OpenBankingConsent> {
    Optional<OpenBankingConsent> findByConsentId(String consentId);
    List<OpenBankingConsent> findByTppClientIdOrderByCreatedAtDesc(String tppClientId);

    /**
     * The consent row, locked ({@code SELECT … FOR UPDATE}) until the transaction ends. A payment
     * takes this lock so two concurrent payments under one consent run one after the other: the
     * second sees the consent CONSUMED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM OpenBankingConsent c WHERE c.consentId = :consentId")
    Optional<OpenBankingConsent> findByConsentIdForUpdate(@Param("consentId") String consentId);

    /**
     * Consents in a non-terminal status whose expiry has passed — the candidates for the
     * expiry job. A null {@code expiry_date} is never {@code < cutoff}, so open-ended
     * consents are correctly excluded.
     */
    List<OpenBankingConsent> findByStatusInAndExpiryDateBefore(
            Collection<ConsentStatus> statuses, Instant cutoff);

    /** A customer's AUTHORISED, unexpired consents that grant {@code scope} (any TPP). */
    @Query("""
            SELECT c FROM OpenBankingConsent c
            WHERE c.customer.id = :customerId
              AND c.status = com.cba.openbanking.ConsentStatus.AUTHORISED
              AND (c.expiryDate IS NULL OR c.expiryDate > :now)
              AND :scope MEMBER OF c.scopes""")
    List<OpenBankingConsent> findUsableByCustomerAndScope(@Param("customerId") UUID customerId,
                                                          @Param("scope") String scope,
                                                          @Param("now") Instant now);

    long countByTppClientIdAndStatus(String tppClientId, ConsentStatus status);
}

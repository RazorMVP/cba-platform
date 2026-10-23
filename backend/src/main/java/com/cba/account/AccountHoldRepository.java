package com.cba.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccountHoldRepository extends JpaRepository<AccountHold, UUID> {

    List<AccountHold> findByAccountIdAndStatus(UUID accountId, AccountHoldStatus status);

    List<AccountHold> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    /**
     * Funds held on the account: ACTIVE holds not yet past their expiry date. A hold whose expiry
     * date has passed stops reducing the balance even before anything marks it EXPIRED (card holds
     * expire with the scheme authorization lifetime).
     */
    @Query("SELECT COALESCE(SUM(h.amount), 0) FROM AccountHold h " +
           "WHERE h.account.id = :accountId AND h.status = 'ACTIVE' " +
           "AND (h.expiryDate IS NULL OR h.expiryDate >= CURRENT_DATE)")
    BigDecimal sumActiveHoldsByAccount(UUID accountId);

    java.util.Optional<AccountHold> findByAccountIdAndExternalReference(UUID accountId, String externalReference);

    /** Finds holds whose expiry date has passed. */
    List<AccountHold> findByStatusAndExpiryDateBefore(AccountHoldStatus status, LocalDate date);

    /**
     * The CoB hold-expiry sweep: ACTIVE holds whose expiry date is before the business date become
     * EXPIRED. They already stopped reducing the balance on that date ({@link #sumActiveHoldsByAccount});
     * this records it. One statement, so a hold captured at the same moment either stays CAPTURED or
     * fails the capture on its version.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE AccountHold h SET h.status = com.cba.account.AccountHoldStatus.EXPIRED, " +
           "h.releasedAt = :now, h.releasedBy = 'hold-expiry-cob-job', h.version = h.version + 1 " +
           "WHERE h.status = com.cba.account.AccountHoldStatus.ACTIVE AND h.expiryDate < :businessDate")
    int expireDue(LocalDate businessDate, java.time.Instant now);
}

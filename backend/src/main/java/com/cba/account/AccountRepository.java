package com.cba.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Page<Account> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    Page<Account> findByCustomerIdAndStatus(UUID customerId, AccountStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(UUID id);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.customer.id = :customerId AND a.status = 'ACTIVE'")
    java.math.BigDecimal sumActiveBalanceByCustomer(UUID customerId);

    long countByStatus(AccountStatus status);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.status = 'ACTIVE'")
    java.math.BigDecimal sumAllActiveBalances();

    /**
     * Finds ACTIVE accounts with no transactions since the cutoff date.
     * Used by the nightly dormancy classification CoB job.
     */
    @Query("SELECT a FROM Account a WHERE a.status = com.cba.account.AccountStatus.ACTIVE " +
           "AND (a.lastTransactionDate IS NULL OR a.lastTransactionDate < :cutoffDate) " +
           "AND a.openedDate < :cutoffDate")
    Page<Account> findCandidatesForDormancy(LocalDate cutoffDate, Pageable pageable);

    // ── Deposit analytics ─────────────────────────────────────────────

    @Query("SELECT a.accountType, COUNT(a), COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.status = 'ACTIVE' GROUP BY a.accountType")
    java.util.List<Object[]> countAndSumByType();

    @Query("SELECT COUNT(a) FROM Account a WHERE a.openedDate >= :start AND a.openedDate <= :end")
    long countOpenedBetween(LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(AVG(a.balance), 0) FROM Account a WHERE a.status = 'ACTIVE'")
    java.math.BigDecimal avgActiveBalance();
}

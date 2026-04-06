package com.cba.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Page<Account> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Account> findByCustomerIdAndStatus(UUID customerId, AccountStatus status, Pageable pageable);

    /**
     * Acquires a pessimistic write lock (SELECT FOR UPDATE) to prevent
     * concurrent balance modifications — essential for the double-entry ledger.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(UUID id);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.customer.id = :customerId AND a.status = 'ACTIVE'")
    java.math.BigDecimal sumActiveBalanceByCustomer(UUID customerId);
}

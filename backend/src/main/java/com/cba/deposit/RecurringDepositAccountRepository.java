package com.cba.deposit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecurringDepositAccountRepository extends JpaRepository<RecurringDepositAccount, UUID> {
    Page<RecurringDepositAccount> findByCustomerId(UUID customerId, Pageable pageable);
    Page<RecurringDepositAccount> findByStatus(RecurringDepositAccount.Status status, Pageable pageable);
}

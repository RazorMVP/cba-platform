package com.cba.deposit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FixedDepositAccountRepository extends JpaRepository<FixedDepositAccount, UUID> {
    Page<FixedDepositAccount> findByCustomerId(UUID customerId, Pageable pageable);
    Page<FixedDepositAccount> findByStatus(FixedDepositAccount.Status status, Pageable pageable);
}

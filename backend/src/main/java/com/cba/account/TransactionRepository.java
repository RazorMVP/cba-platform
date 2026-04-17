package com.cba.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByAccountId(UUID accountId, Pageable pageable);

    Page<Transaction> findByAccountIdAndTransactionDateBetween(
        UUID accountId, Instant from, Instant to, Pageable pageable);

    @Query(value = "SELECT t FROM Transaction t JOIN FETCH t.account ORDER BY t.transactionDate DESC",
           countQuery = "SELECT COUNT(t) FROM Transaction t")
    Page<Transaction> findAllWithAccount(Pageable pageable);
}

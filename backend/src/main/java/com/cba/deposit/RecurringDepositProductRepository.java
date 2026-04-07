package com.cba.deposit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecurringDepositProductRepository extends JpaRepository<RecurringDepositProduct, UUID> {
}

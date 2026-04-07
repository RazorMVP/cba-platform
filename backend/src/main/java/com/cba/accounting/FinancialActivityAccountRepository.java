package com.cba.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FinancialActivityAccountRepository extends JpaRepository<FinancialActivityAccount, UUID> {
    Optional<FinancialActivityAccount> findByFinancialActivity(
            FinancialActivityAccount.FinancialActivity activity);
}

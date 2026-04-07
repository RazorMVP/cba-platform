package com.cba.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GlAccountRepository extends JpaRepository<GlAccount, UUID> {
    Optional<GlAccount> findByGlCode(String glCode);
    List<GlAccount> findByAccountTypeAndDisabledFalse(GlAccount.AccountType accountType);
    List<GlAccount> findByDisabledFalse();
}

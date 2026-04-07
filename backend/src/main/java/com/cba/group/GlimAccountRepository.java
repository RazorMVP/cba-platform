package com.cba.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GlimAccountRepository extends JpaRepository<GlimAccount, UUID> {
    List<GlimAccount> findByGroupId(UUID groupId);
    List<GlimAccount> findByLoanId(UUID loanId);
}

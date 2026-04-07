package com.cba.share;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ShareAccountRepository extends JpaRepository<ShareAccount, UUID> {
    Page<ShareAccount> findByCustomerId(UUID customerId, Pageable pageable);
}

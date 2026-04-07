package com.cba.share;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ShareAccountTransactionRepository extends JpaRepository<ShareAccountTransaction, UUID> {
    Page<ShareAccountTransaction> findByShareAccountId(UUID shareAccountId, Pageable pageable);
}

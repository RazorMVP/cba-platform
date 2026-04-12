package com.cba.account.algorithm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NubanSequenceRepository
        extends JpaRepository<NubanSequence, NubanSequence.NubanSequenceId> {

    /**
     * Pessimistic write lock — ensures only one thread increments the sequence
     * at a time. The {@code REQUIRES_NEW} propagation in {@link NubanAlgorithm}
     * commits the increment immediately so no gap can be reused.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM NubanSequence s WHERE s.id.tenantId = :tenantId AND s.id.accountType = :accountType")
    Optional<NubanSequence> findByTenantAndTypeForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("accountType") String accountType);
}

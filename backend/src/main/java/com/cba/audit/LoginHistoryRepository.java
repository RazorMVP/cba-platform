package com.cba.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {

    Page<LoginHistory> findByStatus(LoginHistory.Status status, Pageable pageable);

    Page<LoginHistory> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    Page<LoginHistory> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    @Query(value = """
        SELECT * FROM login_history
        WHERE (CAST(:status AS varchar) IS NULL OR status = CAST(:status AS varchar))
          AND (CAST(:username AS text) IS NULL OR LOWER(username) LIKE LOWER(CONCAT('%', CAST(:username AS text), '%')))
          AND (CAST(:fromTs AS timestamptz) IS NULL OR created_at >= CAST(:fromTs AS timestamptz))
          AND (CAST(:toTs AS timestamptz) IS NULL OR created_at <= CAST(:toTs AS timestamptz))
        ORDER BY created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM login_history
        WHERE (CAST(:status AS varchar) IS NULL OR status = CAST(:status AS varchar))
          AND (CAST(:username AS text) IS NULL OR LOWER(username) LIKE LOWER(CONCAT('%', CAST(:username AS text), '%')))
          AND (CAST(:fromTs AS timestamptz) IS NULL OR created_at >= CAST(:fromTs AS timestamptz))
          AND (CAST(:toTs AS timestamptz) IS NULL OR created_at <= CAST(:toTs AS timestamptz))
        """,
        nativeQuery = true)
    Page<LoginHistory> search(@Param("status")   String status,
                               @Param("username") String username,
                               @Param("fromTs")   Timestamp fromTs,
                               @Param("toTs")     Timestamp toTs,
                               Pageable pageable);

    @Query("""
        SELECT COUNT(h) FROM LoginHistory h
        WHERE h.status = :status AND h.createdAt >= :since
        """)
    long countByStatusSince(@Param("status") LoginHistory.Status status,
                             @Param("since")  Instant since);

    @Query("""
        SELECT COUNT(DISTINCT h.username) FROM LoginHistory h
        WHERE h.status = 'SUCCESS' AND h.createdAt >= :since
        """)
    long countDistinctUsersLoginSince(@Param("since") Instant since);

    @Query(value = """
        SELECT username, COUNT(*) AS cnt
        FROM login_history
        WHERE status = 'FAILURE' AND created_at >= :since
        GROUP BY username
        ORDER BY cnt DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Object[]> topFailedUsernames(@Param("since") Timestamp since);
}

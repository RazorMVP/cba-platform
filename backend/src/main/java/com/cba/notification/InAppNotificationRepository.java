package com.cba.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    Page<InAppNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfter(OffsetDateTime since);

    @Query("SELECT COUNT(n) FROM InAppNotification n WHERE n.createdAt > :since AND n.severity = 'ERROR'")
    long countErrorsSince(OffsetDateTime since);
}

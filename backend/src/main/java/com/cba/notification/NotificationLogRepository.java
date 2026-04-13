package com.cba.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    Page<NotificationLog> findByEventTypeOrderBySentAtDesc(String eventType, Pageable pageable);
    Page<NotificationLog> findByRecipientIdOrderBySentAtDesc(UUID recipientId, Pageable pageable);
    Page<NotificationLog> findAllByOrderBySentAtDesc(Pageable pageable);
}

package com.cba.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    List<NotificationTemplate> findByActiveTrue();
    Optional<NotificationTemplate> findByEventTypeAndDeliveryMethod(
            String eventType, NotificationTemplate.DeliveryMethod method);
}

package com.cba.card.openbanking.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    List<Webhook> findByActiveTrueOrderByCreatedAtDesc();

    /** All active webhooks that subscribe to a given event type OR have an empty events list (= all events). */
    List<Webhook> findByActiveTrue();
}

package com.cba.card.threeds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThreeDsSessionRepository extends JpaRepository<ThreeDsSession, UUID> {

    Optional<ThreeDsSession> findByAcsTransId(UUID acsTransId);

    List<ThreeDsSession> findByCardIdOrderByCreatedAtDesc(UUID cardId);

    List<ThreeDsSession> findByStatusOrderByCreatedAtDesc(ThreeDsStatus status);
}

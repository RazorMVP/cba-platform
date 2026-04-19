package com.cba.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {

    List<PushDevice> findByUserIdAndActiveTrue(String userId);

    Optional<PushDevice> findByFcmToken(String fcmToken);

    List<PushDevice> findByUserId(String userId);
}

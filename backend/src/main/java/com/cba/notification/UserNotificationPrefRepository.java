package com.cba.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNotificationPrefRepository extends JpaRepository<UserNotificationPref, String> {

    Optional<UserNotificationPref> findByUserId(String userId);
}

package com.cba.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_notification_prefs")
@Getter @Setter @NoArgsConstructor
public class UserNotificationPref {

    @Id
    @Column(length = 120)
    private String userId;

    @Column(nullable = false)
    private OffsetDateTime lastReadAt = OffsetDateTime.now();

    public static UserNotificationPref forUser(String userId) {
        UserNotificationPref p = new UserNotificationPref();
        p.setUserId(userId);
        return p;
    }
}

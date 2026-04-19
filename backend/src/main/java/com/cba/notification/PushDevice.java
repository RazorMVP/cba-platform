package com.cba.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "push_devices")
@Getter @Setter @NoArgsConstructor
public class PushDevice {

    public enum Platform { ANDROID, IOS, WEB }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String userId;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Platform platform;

    @Column(length = 120)
    private String deviceLabel;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime registeredAt = OffsetDateTime.now();

    @Column
    private OffsetDateTime lastSeenAt;
}

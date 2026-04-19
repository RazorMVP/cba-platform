package com.cba.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_history")
@Getter @Setter @NoArgsConstructor
public class LoginHistory {

    public enum Status { SUCCESS, FAILURE, LOCKED, LOGOUT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 120)
    private String userId;

    @Column(length = 120)
    private String username;

    @Column(length = 64)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 200)
    private String failureReason;

    @Column(length = 200)
    private String sessionRef;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static LoginHistory of(String userId, String username,
                                  String ipAddress, String userAgent,
                                  Status status, String failureReason,
                                  String sessionRef) {
        LoginHistory h = new LoginHistory();
        h.userId        = userId;
        h.username      = username;
        h.ipAddress     = ipAddress;
        h.userAgent     = userAgent;
        h.status        = status;
        h.failureReason = failureReason;
        h.sessionRef    = sessionRef;
        return h;
    }
}

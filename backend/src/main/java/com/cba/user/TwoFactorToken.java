package com.cba.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "two_factor_auth_tokens")
@Getter @Setter @NoArgsConstructor
public class TwoFactorToken {

    public enum DeliveryMethod { EMAIL, SMS }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Not serialised: a lazy proxy (Jackson fails on it) and staff profile data; {@link #getUserId()} instead. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private PlatformUser user;

    /**
     * The platform user the code was issued to, serialised as {@code userId}. Not named
     * {@code getUserId}: Spring Data would then read {@code findByUserId} as a property.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    public java.util.UUID issuedToUserId() {
        return user != null ? user.getId() : null;
    }

    @Column(nullable = false, length = 20)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 20)
    private DeliveryMethod deliveryMethod = DeliveryMethod.EMAIL;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

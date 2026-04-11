package com.cba.card.threeds;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single-use OTP for challenge-based 3DS authentication.
 *
 * <p>The plaintext OTP is never stored. Only the HMAC-SHA256 hash
 * (using the same key as PAN hash derivation) is persisted.
 */
@Entity
@Table(name = "threeds_otp_tokens")
@Getter @Setter @NoArgsConstructor
public class ThreeDsOtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** HMAC-SHA256(otpPlaintext, hmac_key) — prevents plaintext storage. */
    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

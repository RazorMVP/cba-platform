package com.cba.wallet;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "qr_payment_tokens")
@Getter @Setter @NoArgsConstructor
public class QrPaymentToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 600)
    private String token;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "preset_amount", precision = 19, scale = 4)
    private BigDecimal presetAmount;

    @Column(length = 200)
    private String reference;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}

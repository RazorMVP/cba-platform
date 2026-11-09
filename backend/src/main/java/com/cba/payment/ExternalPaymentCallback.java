package com.cba.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One callback from the external payments gateway, accepted or not (V67): the money path's audit trail. */
@Entity
@Table(name = "external_payment_callbacks")
@Getter
@Setter
public class ExternalPaymentCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 50)
    private String reference;

    @Column(name = "network_reference", length = 64)
    private String networkReference;

    @Column(length = 20)
    private String outcome;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "signature_ok", nullable = false)
    private boolean signatureOk;

    /** APPLIED, REPEAT, or the refusal code (INVALID_SIGNATURE, PAYMENT_NOT_PROCESSING, …). */
    @Column(nullable = false, length = 40)
    private String result;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "remote_address", length = 64)
    private String remoteAddress;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();
}

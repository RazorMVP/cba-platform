package com.cba.customer;

import com.cba.common.audit.AuditableEntity;
import com.cba.common.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "external_id", unique = true, nullable = false, length = 50)
    private String externalId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "first_name_encrypted", nullable = false)
    private String firstName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "last_name_encrypted", nullable = false)
    private String lastName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email_encrypted", nullable = false)
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_encrypted")
    private String phone;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id_encrypted")
    private String nationalId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus = KycStatus.PENDING_KYC;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Keycloak user UUID (JWT `sub` claim) — set when customer registers for self-service. */
    @Column(name = "keycloak_id", unique = true, length = 100)
    private String keycloakId;

    // ── Lifecycle dates ──────────────────────────────────────────────────────
    @Column(name = "activation_date")
    private LocalDate activationDate;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @Column(name = "rejection_date")
    private LocalDate rejectionDate;

    @Column(name = "withdrawal_date")
    private LocalDate withdrawalDate;

    // ── Lifecycle reasons ────────────────────────────────────────────────────
    @Column(name = "closure_reason", length = 500)
    private String closureReason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "withdrawal_reason", length = 500)
    private String withdrawalReason;

    // ── Staff / office assignment ────────────────────────────────────────────
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "office_id")
    private UUID officeId;

    // ── Inter-branch transfer ────────────────────────────────────────────────
    @Column(name = "transfer_to_office_id")
    private UUID transferToOfficeId;

    @Column(name = "transfer_date")
    private LocalDate transferDate;

    @Column(name = "transfer_note", length = 500)
    private String transferNote;
}

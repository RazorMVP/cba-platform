package com.cba.account.algorithm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the last-used serial number for NUBAN generation per tenant per account type.
 * The composite primary key (tenant_id, account_type) ensures each deployment slot
 * has its own independent counter — CBA Nigeria's SAVINGS sequence is completely
 * isolated from CBA Ghana's (even if Ghana never uses NUBAN).
 */
@Entity
@Table(name = "nuban_sequences")
@Getter
@Setter
@NoArgsConstructor
public class NubanSequence {

    @EmbeddedId
    private NubanSequenceId id;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence = 0L;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    // ── Embeddable composite key ──────────────────────────────────────

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class NubanSequenceId implements java.io.Serializable {

        @Column(name = "tenant_id", nullable = false)
        private UUID tenantId;

        @Column(name = "account_type", nullable = false, length = 50)
        private String accountType;

        public NubanSequenceId(UUID tenantId, String accountType) {
            this.tenantId    = tenantId;
            this.accountType = accountType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NubanSequenceId that)) return false;
            return java.util.Objects.equals(tenantId, that.tenantId)
                    && java.util.Objects.equals(accountType, that.accountType);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, accountType);
        }
    }
}

package com.cba.share;

import com.cba.customer.Customer;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "share_accounts")
@Getter @Setter @NoArgsConstructor
public class ShareAccount {

    public enum Status { SUBMITTED, APPROVED, ACTIVE, CLOSED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ShareProduct product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    @Column(name = "requested_shares")
    private Long requestedShares;

    @Column(name = "approved_shares")
    private Long approvedShares;

    @Column(name = "total_shares_held")
    private Long totalSharesHeld = 0L;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "submitted_on_date")
    private LocalDate submittedOnDate;

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @Column(name = "activated_on_date")
    private LocalDate activatedOnDate;

    @Column(name = "closed_on_date")
    private LocalDate closedOnDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

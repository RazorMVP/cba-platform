package com.cba.system;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_number_formats")
@Getter @Setter @NoArgsConstructor
public class AccountNumberFormat {

    public enum AccountType { LOAN, SAVINGS, CLIENT, SHARE }
    public enum PrefixType { NONE, OFFICE_NAME, LOAN_PRODUCT_SHORT_NAME, SAVINGS_PRODUCT_SHORT_NAME, CLIENT_TYPE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "prefix_type", length = 50)
    private PrefixType prefixType = PrefixType.NONE;

    @Column(name = "prefix_character", length = 10)
    private String prefixCharacter;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}

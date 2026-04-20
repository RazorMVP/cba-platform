package com.cba.wallet;

import com.cba.account.Account;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pocket_accounts")
@Getter @Setter @NoArgsConstructor
public class PocketAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pocket_id", nullable = false)
    private Pocket pocket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @CreationTimestamp
    @Column(name = "linked_at", updatable = false)
    private OffsetDateTime linkedAt;
}

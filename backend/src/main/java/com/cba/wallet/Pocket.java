package com.cba.wallet;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pockets")
@Getter @Setter @NoArgsConstructor
public class Pocket extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PocketStatus status = PocketStatus.ACTIVE;

    @OneToMany(mappedBy = "pocket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PocketAccount> pocketAccounts = new ArrayList<>();

    public enum PocketStatus { ACTIVE, CLOSED }
}

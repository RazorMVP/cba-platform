package com.cba.group;

import com.cba.customer.Customer;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "collection_sheet_items")
@Getter @Setter @NoArgsConstructor
public class CollectionSheetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_sheet_id", nullable = false)
    private CollectionSheet collectionSheet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "due_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal dueAmount;

    @Column(name = "collected_amount", precision = 19, scale = 4)
    private BigDecimal collectedAmount;

    @Column(name = "is_collected", nullable = false)
    private boolean collected = false;
}

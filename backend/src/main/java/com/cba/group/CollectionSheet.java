package com.cba.group;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "collection_sheets",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "meeting_date"}))
@Getter @Setter @NoArgsConstructor
public class CollectionSheet {

    public enum Status { PENDING, PROCESSED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @OneToMany(mappedBy = "collectionSheet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CollectionSheetItem> items = new ArrayList<>();
}

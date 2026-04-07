package com.cba.social;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "maker_checkers")
@Getter @Setter @NoArgsConstructor
public class MakerChecker {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "action_name", nullable = false, length = 100)
    private String actionName;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "command_as_json", nullable = false, columnDefinition = "TEXT")
    private String commandAsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "made_by_user_id", nullable = false)
    private UUID madeByUserId;

    @Column(name = "checked_by_user_id")
    private UUID checkedByUserId;

    @Column(name = "made_on_date", nullable = false, updatable = false)
    private OffsetDateTime madeOnDate = OffsetDateTime.now();

    @Column(name = "checked_on_date")
    private OffsetDateTime checkedOnDate;

    @Column(name = "processing_result", columnDefinition = "TEXT")
    private String processingResult;

    @Version
    private Long version;
}

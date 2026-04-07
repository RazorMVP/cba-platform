package com.cba.accounting;

import com.cba.office.Office;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "gl_closures",
       uniqueConstraints = @UniqueConstraint(columnNames = {"office_id", "closing_date"}))
@Getter @Setter @NoArgsConstructor
public class GlClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(columnDefinition = "TEXT")
    private String comments;
}

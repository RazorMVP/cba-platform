package com.cba.system;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "survey_questions")
@Getter @Setter @NoArgsConstructor
public class SurveyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(nullable = false, length = 100)
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo = 0;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<SurveyResponse> responses = new ArrayList<>();
}

package com.cba.system;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "survey_responses")
@Getter @Setter @NoArgsConstructor
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private SurveyQuestion question;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false)
    private int value = 0;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo = 0;
}

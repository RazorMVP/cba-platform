package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SurveyService — unit tests")
class SurveyServiceTest {

    @Mock SurveyRepository surveyRepository;
    @Mock SurveyScorecardRepository scorecardRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks SurveyService service;

    private UUID surveyId;
    private Survey survey;

    @BeforeEach
    void setUp() {
        surveyId = UUID.randomUUID();
        survey = new Survey();
        survey.setId(surveyId);
        survey.setKey("PPI_KE");
        survey.setName("Kenya PPI Survey");
        survey.setActive(true);
    }

    @Nested
    @DisplayName("List and Get")
    class ListAndGet {

        @Test
        @DisplayName("listSurveys returns page")
        void listSurveys_returnsPage() {
            when(surveyRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(survey)));
            assertThat(service.listSurveys(Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getSurvey returns survey when found")
        void getSurvey_found() {
            when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));
            assertThat(service.getSurvey(surveyId).getName()).isEqualTo("Kenya PPI Survey");
        }

        @Test
        @DisplayName("getSurvey throws when not found")
        void getSurvey_notFound_throws() {
            when(surveyRepository.findById(surveyId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getSurvey(surveyId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("getSurveyByKey returns survey when found")
        void getSurveyByKey_found() {
            when(surveyRepository.findByKey("PPI_KE")).thenReturn(Optional.of(survey));
            assertThat(service.getSurveyByKey("PPI_KE").getKey()).isEqualTo("PPI_KE");
        }

        @Test
        @DisplayName("getSurveyByKey throws when not found")
        void getSurveyByKey_notFound_throws() {
            when(surveyRepository.findByKey("UNKNOWN")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getSurveyByKey("UNKNOWN"))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Create and Update")
    class CreateAndUpdate {

        @Test
        @DisplayName("createSurvey saves survey")
        void createSurvey_success() {
            when(surveyRepository.save(any())).thenReturn(survey);

            SurveyService.CreateSurveyRequest req = new SurveyService.CreateSurveyRequest(
                "PPI_KE", "Kenya PPI Survey", "KE", "Poverty probability index",
                LocalDate.now(), LocalDate.now().plusYears(1), true
            );
            Survey result = service.createSurvey(req);
            assertThat(result.getKey()).isEqualTo("PPI_KE");
            verify(surveyRepository).save(any(Survey.class));
        }

        @Test
        @DisplayName("updateSurvey saves changes")
        void updateSurvey_success() {
            when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));
            when(surveyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SurveyService.CreateSurveyRequest req = new SurveyService.CreateSurveyRequest(
                "PPI_KE_V2", "Kenya PPI v2", "KE", "Updated", null, null, true
            );
            Survey result = service.updateSurvey(surveyId, req);
            assertThat(result.getKey()).isEqualTo("PPI_KE_V2");
        }

        @Test
        @DisplayName("deleteSurvey removes survey")
        void deleteSurvey_success() {
            when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));

            assertThatCode(() -> service.deleteSurvey(surveyId)).doesNotThrowAnyException();
            verify(surveyRepository).delete(survey);
        }
    }

    @Nested
    @DisplayName("Scorecards")
    class Scorecards {

        @Test
        @DisplayName("submitScorecard saves scorecard with scores")
        void submitScorecard_success() {
            when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));
            SurveyScorecard sc = new SurveyScorecard();
            sc.setId(UUID.randomUUID());
            when(scorecardRepository.save(any())).thenReturn(sc);

            SurveyService.SubmitScorecardRequest req = new SurveyService.SubmitScorecardRequest(
                surveyId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), LocalDate.now(), "KE",
                List.of(new SurveyService.ScoreEntry(UUID.randomUUID(), UUID.randomUUID(), 3))
            );
            SurveyScorecard result = service.submitScorecard(req);
            assertThat(result).isNotNull();
            verify(scorecardRepository).save(any(SurveyScorecard.class));
        }

        @Test
        @DisplayName("getScorecards returns scorecards for survey")
        void getScorecards_success() {
            when(scorecardRepository.findBySurveyId(surveyId)).thenReturn(List.of());
            assertThat(service.getScorecards(surveyId)).isEmpty();
        }
    }
}

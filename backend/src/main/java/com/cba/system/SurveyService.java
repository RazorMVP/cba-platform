package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SurveyService {

    public record CreateSurveyRequest(
        String key, String name, String countryCode, String description,
        LocalDate validFrom, LocalDate validTo, boolean active
    ) {}

    public record SubmitScorecardRequest(
        UUID surveyId, UUID customerId, UUID userId,
        LocalDate startDate, LocalDate endDate, String countryCode,
        List<ScoreEntry> scores
    ) {}

    public record ScoreEntry(UUID questionId, UUID responseId, int value) {}

    private final SurveyRepository           surveyRepository;
    private final SurveyScorecardRepository  scorecardRepository;
    private final AuditLogService            auditLogService;

    @Transactional(readOnly = true)
    public Page<Survey> listSurveys(Pageable p) { return surveyRepository.findAll(p); }

    @Transactional(readOnly = true)
    public Survey getSurvey(UUID id) {
        return surveyRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Survey", id));
    }

    @Transactional(readOnly = true)
    public Survey getSurveyByKey(String key) {
        return surveyRepository.findByKey(key)
            .orElseThrow(() -> CbaException.notFound("Survey", key));
    }

    @Transactional
    public Survey createSurvey(CreateSurveyRequest req) {
        Survey s = new Survey();
        applyRequest(s, req);
        Survey saved = surveyRepository.save(s);
        auditLogService.log("Survey", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public Survey updateSurvey(UUID id, CreateSurveyRequest req) {
        Survey s = getSurvey(id);
        applyRequest(s, req);
        Survey saved = surveyRepository.save(s);
        auditLogService.log("Survey", id.toString(), "UPDATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteSurvey(UUID id) {
        surveyRepository.delete(getSurvey(id));
        auditLogService.log("Survey", id.toString(), "DELETE", null, null);
    }

    @Transactional
    public SurveyScorecard submitScorecard(SubmitScorecardRequest req) {
        Survey survey = getSurvey(req.surveyId());
        SurveyScorecard sc = new SurveyScorecard();
        sc.setSurvey(survey);
        sc.setCustomerId(req.customerId());
        sc.setUserId(req.userId());
        sc.setStartDate(req.startDate());
        sc.setEndDate(req.endDate());
        sc.setCountryCode(req.countryCode());
        if (req.scores() != null) {
            for (ScoreEntry e : req.scores()) {
                SurveyScorecardScore score = new SurveyScorecardScore();
                score.setScorecard(sc);
                score.setQuestionId(e.questionId());
                score.setResponseId(e.responseId());
                score.setValue(e.value());
                sc.getScores().add(score);
            }
        }
        SurveyScorecard saved = scorecardRepository.save(sc);
        auditLogService.log("SurveyScorecard", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SurveyScorecard> getScorecards(UUID surveyId) {
        return scorecardRepository.findBySurveyId(surveyId);
    }

    private void applyRequest(Survey s, CreateSurveyRequest req) {
        s.setKey(req.key());
        s.setName(req.name());
        s.setCountryCode(req.countryCode());
        s.setDescription(req.description());
        s.setValidFrom(req.validFrom());
        s.setValidTo(req.validTo());
        s.setActive(req.active());
    }
}

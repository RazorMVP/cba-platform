package com.cba.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SurveyScorecardRepository extends JpaRepository<SurveyScorecard, UUID> {
    List<SurveyScorecard> findBySurveyIdAndCustomerId(UUID surveyId, UUID customerId);
    List<SurveyScorecard> findBySurveyId(UUID surveyId);
}

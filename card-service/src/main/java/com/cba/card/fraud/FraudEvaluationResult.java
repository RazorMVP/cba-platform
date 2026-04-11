package com.cba.card.fraud;

import java.util.List;

public record FraudEvaluationResult(
        int                    totalScore,
        FraudDecision          decision,
        List<FraudRuleResult>  ruleResults
) {
    public boolean approved()  { return decision == FraudDecision.APPROVE; }
    public boolean stepUp()    { return decision == FraudDecision.STEP_UP; }
    public boolean declined()  { return decision == FraudDecision.DECLINE; }
}

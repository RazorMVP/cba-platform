package com.cba.card.fraud;

/**
 * Result of evaluating a single fraud rule.
 *
 * @param ruleId           Rule identifier (matches fraud_rules.rule_id)
 * @param triggered        Whether this rule fired
 * @param scoreContribution Score added to total (0 if not triggered)
 * @param hardBlock        True for weight=100 rules — bypasses thresholds and forces DECLINE immediately
 */
public record FraudRuleResult(
        String  ruleId,
        boolean triggered,
        int     scoreContribution,
        boolean hardBlock
) {
    public static FraudRuleResult notTriggered(String ruleId) {
        return new FraudRuleResult(ruleId, false, 0, false);
    }

    public static FraudRuleResult triggered(String ruleId, int weight) {
        return new FraudRuleResult(ruleId, true, weight, weight >= 100);
    }
}

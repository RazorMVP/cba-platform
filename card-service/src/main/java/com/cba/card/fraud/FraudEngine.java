package com.cba.card.fraud;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core fraud evaluation engine.
 *
 * <p>Evaluates all enabled rules in sequence, sums contributions, and
 * determines the final decision based on configurable thresholds.
 *
 * <p>Hard-block rules (weight ≥ 100: CARD_BLOCKED, CARD_EXPIRED, PIN_RETRY_EXCEEDED)
 * short-circuit evaluation immediately — no need to run remaining rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudEngine {

    private final FraudRuleEntityRepository ruleRepository;
    private final AuthorizationLogRepository authLogRepository;
    private final FraudScoreLogRepository   scoreLogRepository;

    @Value("${card.fraud.approve-threshold:30}")
    private int approveThreshold;

    @Value("${card.fraud.step-up-threshold:70}")
    private int stepUpThreshold;

    @Transactional
    public FraudEvaluationResult evaluate(FraudContext ctx) {
        List<FraudRuleEntity> enabledRules = ruleRepository.findByEnabledTrue();

        // Build a map of ruleId → weight+params for O(1) lookup
        Map<String, FraudRuleEntity> ruleMap = enabledRules.stream()
                .collect(Collectors.toMap(FraudRuleEntity::getRuleId, r -> r));

        List<FraudRuleResult> results = new ArrayList<>();
        int totalScore = 0;

        // ── Hard-block rules first (weight 100) ────────────────────────────
        if (ctx.isCardBlocked()) {
            FraudRuleResult r = FraudRuleResult.triggered("CARD_BLOCKED", 100);
            results.add(r);
            return buildResult(ctx, results, 100, FraudDecision.DECLINE);
        }
        if (ctx.isCardExpired()) {
            FraudRuleResult r = FraudRuleResult.triggered("CARD_EXPIRED", 100);
            results.add(r);
            return buildResult(ctx, results, 100, FraudDecision.DECLINE);
        }
        if (ctx.card() != null && ctx.card().getPinRetryCount() >= 3) {
            FraudRuleEntity rule = ruleMap.get("PIN_RETRY_EXCEEDED");
            int maxRetries = rule != null ? intParam(rule.getParams(), "max_retries", 3) : 3;
            if (ctx.card().getPinRetryCount() >= maxRetries) {
                FraudRuleResult r = FraudRuleResult.triggered("PIN_RETRY_EXCEEDED", 100);
                results.add(r);
                return buildResult(ctx, results, 100, FraudDecision.DECLINE);
            }
        }

        // ── SINGLE_AMOUNT_LIMIT ────────────────────────────────────────────
        FraudRuleEntity limitRule = ruleMap.get("SINGLE_AMOUNT_LIMIT");
        if (limitRule != null && ctx.card() != null) {
            // Limit checked elsewhere (CardLimitService), but we still score it
            // For demo: flag if amount > 100000 cents ($1000) with no per-txn limit override
            BigDecimal amountThreshold = BigDecimal.valueOf(
                    intParam(limitRule.getParams(), "threshold_minor_units", 100000));
            if (ctx.amount() != null && ctx.amount().compareTo(amountThreshold) > 0) {
                results.add(FraudRuleResult.triggered("SINGLE_AMOUNT_LIMIT", limitRule.getWeight()));
                totalScore += limitRule.getWeight();
            } else {
                results.add(FraudRuleResult.notTriggered("SINGLE_AMOUNT_LIMIT"));
            }
        }

        // ── BLOCKED_MCC ────────────────────────────────────────────────────
        FraudRuleEntity mccRule = ruleMap.get("BLOCKED_MCC");
        if (mccRule != null && ctx.mcc() != null) {
            @SuppressWarnings("unchecked")
            List<String> blockedMccs = (List<String>) mccRule.getParams()
                    .getOrDefault("blocked_mccs", List.of());
            if (blockedMccs.contains(ctx.mcc())) {
                results.add(FraudRuleResult.triggered("BLOCKED_MCC", mccRule.getWeight()));
                totalScore += mccRule.getWeight();
            } else {
                results.add(FraudRuleResult.notTriggered("BLOCKED_MCC"));
            }
        }

        // ── CNP_DEBIT ──────────────────────────────────────────────────────
        FraudRuleEntity cnpRule = ruleMap.get("CNP_DEBIT");
        if (cnpRule != null && ctx.isCardNotPresent() &&
                ctx.cardType() == com.cba.card.card.CardType.DEBIT) {
            results.add(FraudRuleResult.triggered("CNP_DEBIT", cnpRule.getWeight()));
            totalScore += cnpRule.getWeight();
        } else if (cnpRule != null) {
            results.add(FraudRuleResult.notTriggered("CNP_DEBIT"));
        }

        // ── VELOCITY_LIMIT ─────────────────────────────────────────────────
        FraudRuleEntity velocityRule = ruleMap.get("VELOCITY_LIMIT");
        if (velocityRule != null && ctx.card() != null) {
            int maxTxns      = intParam(velocityRule.getParams(), "max_transactions", 5);
            int windowMinutes = intParam(velocityRule.getParams(), "window_minutes", 10);
            OffsetDateTime since = OffsetDateTime.now().minusMinutes(windowMinutes);
            long recentCount = authLogRepository.countApprovedSince(ctx.card().getId(), since);
            if (recentCount >= maxTxns) {
                results.add(FraudRuleResult.triggered("VELOCITY_LIMIT", velocityRule.getWeight()));
                totalScore += velocityRule.getWeight();
            } else {
                results.add(FraudRuleResult.notTriggered("VELOCITY_LIMIT"));
            }
        }

        // ── DUPLICATE_TRANSACTION ──────────────────────────────────────────
        FraudRuleEntity dupRule = ruleMap.get("DUPLICATE_TRANSACTION");
        if (dupRule != null && ctx.card() != null && ctx.amount() != null && ctx.merchantId() != null) {
            int windowMinutes = intParam(dupRule.getParams(), "window_minutes", 2);
            OffsetDateTime since = OffsetDateTime.now().minusMinutes(windowMinutes);
            boolean isDuplicate = authLogRepository.existsDuplicate(
                    ctx.card().getId(), ctx.amount(), ctx.merchantId(), since);
            if (isDuplicate) {
                results.add(FraudRuleResult.triggered("DUPLICATE_TRANSACTION", dupRule.getWeight()));
                totalScore += dupRule.getWeight();
            } else {
                results.add(FraudRuleResult.notTriggered("DUPLICATE_TRANSACTION"));
            }
        }

        // Cap at 100
        totalScore = Math.min(totalScore, 100);

        FraudDecision decision = totalScore < approveThreshold ? FraudDecision.APPROVE
                : totalScore < stepUpThreshold ? FraudDecision.STEP_UP
                : FraudDecision.DECLINE;

        log.info("Fraud evaluation: card={} score={} decision={}",
                ctx.card() != null ? ctx.card().getId() : "unknown", totalScore, decision);

        return buildResult(ctx, results, totalScore, decision);
    }

    private FraudEvaluationResult buildResult(FraudContext ctx, List<FraudRuleResult> results,
                                               int score, FraudDecision decision) {
        return new FraudEvaluationResult(score, decision, results);
    }

    private int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }
}

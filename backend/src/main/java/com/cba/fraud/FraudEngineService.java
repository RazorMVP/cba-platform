package com.cba.fraud;

import com.cba.common.exception.CbaException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core fraud rule evaluation engine for banking transactions.
 *
 * Blocking checks (velocity, blacklist) are called synchronously before
 * transaction commit. Monitoring checks (structuring, AML) are triggered
 * async after commit via @TransactionalEventListener to avoid deadlocks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudEngineService {

    private final FraudRuleRepository       fraudRuleRepository;
    private final FraudAlertRepository      alertRepository;
    private final BlacklistEntryRepository  blacklistRepository;
    private final CustomerRiskScoreRepository riskScoreRepository;
    private final JdbcTemplate              jdbcTemplate;
    private final ObjectMapper              objectMapper;

    // ─── Blocking checks (called synchronously before TX commit) ───────────

    /**
     * Checks blocking fraud rules. Throws CbaException if a blocking rule fires.
     * Non-blocking violations raise alerts but do not stop the transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void preTransactionCheck(UUID customerId, UUID accountId, BigDecimal amount,
                                    String currencyCode, String transactionType) {
        List<FraudRule> blockingRules = fraudRuleRepository.findByEnabledTrueAndBlockingTrueOrderByNameAsc();

        for (FraudRule rule : blockingRules) {
            switch (rule.getRuleType()) {
                case "VELOCITY_LIMIT" -> {
                    if (checkVelocityLimit(accountId, amount, rule)) {
                        raiseAlert(rule, customerId, accountId, null,
                            "Velocity limit exceeded on account " + accountId, "HIGH", transactionType);
                        throw CbaException.badRequest("FRAUD_VELOCITY_LIMIT",
                            "Transaction blocked: velocity limit exceeded");
                    }
                }
                case "BLACKLIST_HIT" -> {
                    if (customerId != null && isBlacklisted(customerId)) {
                        raiseAlert(rule, customerId, accountId, null,
                            "Blacklisted customer attempted transaction", "CRITICAL", transactionType);
                        throw CbaException.badRequest("FRAUD_BLACKLIST_HIT",
                            "Transaction blocked: customer is on the sanctions list");
                    }
                }
                case "LARGE_CASH_TRANSACTION" -> {
                    long threshold = resolveThreshold(rule.getParams(), currencyCode, 1000000L);
                    if (amount.movePointRight(2).longValue() >= threshold) {
                        raiseAlert(rule, customerId, accountId, null,
                            "Large cash transaction: " + amount + " " + currencyCode, "HIGH", transactionType);
                        // Not blocking by default — just alert (CTR reporting)
                    }
                }
            }
        }

        // Non-blocking rules that still need a synchronous alert
        fraudRuleRepository.findByEnabledTrueOrderByNameAsc().stream()
            .filter(r -> !r.isBlocking())
            .filter(r -> r.getRuleType().equals("LARGE_CASH_TRANSACTION"))
            .forEach(rule -> {
                long threshold = resolveThreshold(rule.getParams(), currencyCode, 1000000L);
                if (amount.movePointRight(2).longValue() >= threshold) {
                    raiseAlert(rule, customerId, accountId, null,
                        "Large cash transaction (CTR): " + amount + " " + currencyCode,
                        "HIGH", transactionType);
                }
            });
    }

    // ─── Async monitoring (called after TX commit via event listener) ───────

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTransactionCommitted(TransactionFraudEvent event) {
        fraudRuleRepository.findByEnabledTrueOrderByNameAsc().stream()
            .filter(r -> !r.isBlocking())
            .forEach(rule -> {
                switch (rule.getRuleType()) {
                    case "STRUCTURING_DETECTION" -> checkStructuring(event, rule);
                    case "RAPID_FUND_MOVEMENT"   -> checkRapidFundMovement(event, rule);
                }
            });

        // Recalculate risk score asynchronously
        if (event.customerId() != null) {
            recalculateRiskScore(event.customerId());
        }
    }

    // ─── Blacklist screening ─────────────────────────────────────────────────

    public boolean isBlacklisted(UUID customerId) {
        // Look up customer name/national ID from customers table for name-matching
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT national_id_encrypted, first_name_encrypted FROM customers WHERE id = ?",
            customerId);
        if (rows.isEmpty()) return false;

        // Check direct customer ID blacklist
        List<BlacklistEntry> hits = blacklistRepository
            .findActiveByTypeAndValue("CUSTOMER", customerId.toString(), Instant.now());
        return !hits.isEmpty();
    }

    public boolean isValueBlacklisted(String entityType, String value) {
        return !blacklistRepository
            .findActiveByTypeAndValue(entityType, value, Instant.now())
            .isEmpty();
    }

    // ─── Risk score ──────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculateRiskScore(UUID customerId) {
        CustomerRiskScore score = riskScoreRepository.findByCustomerId(customerId)
            .orElseGet(() -> {
                CustomerRiskScore s = new CustomerRiskScore();
                s.setCustomerId(customerId);
                return s;
            });

        long openAlerts = alertRepository.countByCustomerIdAndStatus(customerId, "OPEN");
        long confirmedCases = alertRepository.countByCustomerIdAndStatus(customerId, "CLOSED_CONFIRMED");
        boolean blacklisted = isBlacklisted(customerId);

        int computed = (int) Math.min(100,
            (openAlerts * 10) + (confirmedCases * 25) + (blacklisted ? 50 : 0));

        score.setOpenAlertsCount((int) openAlerts);
        score.setConfirmedCasesCount((int) confirmedCases);
        score.setBlacklistHits(blacklisted ? 1 : 0);
        score.setScore(computed);
        score.setRiskLevel(computed >= 70 ? "HIGH" : computed >= 30 ? "MEDIUM" : "LOW");
        score.setCalculatedAt(Instant.now());

        riskScoreRepository.save(score);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private boolean checkVelocityLimit(UUID accountId, BigDecimal amount, FraudRule rule) {
        Map<String, Object> params = parseParams(rule.getParams());
        int maxCount = ((Number) params.getOrDefault("max_count", 10)).intValue();
        int windowMinutes = ((Number) params.getOrDefault("window_minutes", 60)).intValue();

        String sql = """
            SELECT COUNT(*) FROM transactions
            WHERE account_id = ?
              AND created_at > NOW() - INTERVAL '%d minutes'
            """.formatted(windowMinutes);

        Long count = jdbcTemplate.queryForObject(sql, Long.class, accountId);
        return count != null && count >= maxCount;
    }

    private void checkStructuring(TransactionFraudEvent event, FraudRule rule) {
        Map<String, Object> params = parseParams(rule.getParams());
        long threshold = resolveThreshold(rule.getParams(), event.currencyCode(), 1000000L);
        int windowMinutes = ((Number) params.getOrDefault("window_minutes", 1440)).intValue();
        int minCount = ((Number) params.getOrDefault("min_count", 3)).intValue();

        String sql = """
            SELECT COUNT(*) FROM transactions
            WHERE account_id = ?
              AND created_at > NOW() - INTERVAL '%d minutes'
              AND amount * 100 BETWEEN ? * 0.8 AND ?
            """.formatted(windowMinutes);

        long floor = (long) (threshold * 0.8);
        Long count = jdbcTemplate.queryForObject(sql, Long.class,
            event.accountId(), floor, threshold);

        if (count != null && count >= minCount) {
            raiseAlert(rule, event.customerId(), event.accountId(), event.transactionId(),
                "Potential structuring: " + count + " transactions near threshold in " + windowMinutes + " min",
                "CRITICAL", event.transactionType());
        }
    }

    private void checkRapidFundMovement(TransactionFraudEvent event, FraudRule rule) {
        Map<String, Object> params = parseParams(rule.getParams());
        int windowMinutes = ((Number) params.getOrDefault("window_minutes", 60)).intValue();
        int minCount = ((Number) params.getOrDefault("min_count", 5)).intValue();

        String sql = """
            SELECT COUNT(DISTINCT destination_account_id) FROM payments
            WHERE source_account_id = ?
              AND created_at > NOW() - INTERVAL '%d minutes'
              AND status = 'COMPLETED'
            """.formatted(windowMinutes);

        Long distinctDest = jdbcTemplate.queryForObject(sql, Long.class, event.accountId());

        if (distinctDest != null && distinctDest >= minCount) {
            raiseAlert(rule, event.customerId(), event.accountId(), event.transactionId(),
                "Rapid fund movement: " + distinctDest + " distinct destinations in " + windowMinutes + " min",
                "HIGH", event.transactionType());
        }
    }

    private void raiseAlert(FraudRule rule, UUID customerId, UUID accountId,
                            UUID transactionId, String description,
                            String severity, String alertType) {
        FraudAlert alert = new FraudAlert();
        alert.setRuleId(rule.getId());
        alert.setRuleName(rule.getName());
        alert.setCustomerId(customerId);
        alert.setAccountId(accountId);
        alert.setTransactionId(transactionId);
        alert.setSeverity(severity);
        alert.setStatus("OPEN");
        alert.setAlertType(alertType != null ? alertType : rule.getRuleType());
        alert.setDetails("{\"description\": \"" + description.replace("\"", "'") + "\"}");
        alertRepository.save(alert);
        log.warn("Fraud alert raised: rule={} severity={} desc={}", rule.getName(), severity, description);
    }

    private long resolveThreshold(String paramsJson, String currencyCode, long defaultThreshold) {
        Map<String, Object> params = parseParams(paramsJson);
        if (params.containsKey("thresholds")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> thresholds = (Map<String, Object>) params.get("thresholds");
            if (thresholds.containsKey(currencyCode)) {
                return ((Number) thresholds.get(currencyCode)).longValue();
            }
            if (thresholds.containsKey("default")) {
                return ((Number) thresholds.get("default")).longValue();
            }
        }
        return defaultThreshold;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}

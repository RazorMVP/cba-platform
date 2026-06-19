package com.cba.card.fraud;

import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.card.Card;
import com.cba.card.card.CardStatus;
import com.cba.card.card.CardType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FraudEngine} — the risk-scoring engine that decides
 * APPROVE / STEP_UP / DECLINE for every card authorization. False approvals
 * here let fraudulent transactions through; false declines block good ones.
 *
 * <p>All collaborators are JPA repositories (interfaces); {@code Card} is a real
 * entity. Thresholds are normally {@code @Value}-injected, so they are set via
 * reflection (30 / 70 — the documented defaults).
 */
@ExtendWith(MockitoExtension.class)
class FraudEngineTest {

    @Mock FraudRuleEntityRepository ruleRepository;
    @Mock AuthorizationLogRepository authLogRepository;
    @Mock FraudScoreLogRepository scoreLogRepository;

    private FraudEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FraudEngine(ruleRepository, authLogRepository, scoreLogRepository);
        ReflectionTestUtils.setField(engine, "approveThreshold", 30);
        ReflectionTestUtils.setField(engine, "stepUpThreshold", 70);
    }

    // ── Hard-block rules (immediate DECLINE, score 100) ───────────────────────

    @Test
    @DisplayName("a BLOCKED card declines immediately with score 100")
    void blockedCardDeclines() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of());
        FraudEvaluationResult r = engine.evaluate(ctx(card(CardStatus.BLOCKED, "9912", 0), amount(1000), null));
        assertThat(r.decision()).isEqualTo(FraudDecision.DECLINE);
        assertThat(r.totalScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("an EXPIRED card declines immediately with score 100")
    void expiredCardDeclines() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of());
        FraudEvaluationResult r = engine.evaluate(ctx(card(CardStatus.ACTIVE, "0001", 0), amount(1000), null));
        assertThat(r.decision()).isEqualTo(FraudDecision.DECLINE);
        assertThat(r.totalScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("PIN retries >= 3 decline immediately with score 100")
    void pinRetryExceededDeclines() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of());
        FraudEvaluationResult r = engine.evaluate(ctx(card(CardStatus.ACTIVE, "9912", 3), amount(1000), null));
        assertThat(r.decision()).isEqualTo(FraudDecision.DECLINE);
        assertThat(r.totalScore()).isEqualTo(100);
    }

    // ── Scored rules ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a clean transaction with no rules is APPROVE / score 0")
    void cleanTransactionApproves() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of());
        FraudEvaluationResult r = engine.evaluate(ctx(card(CardStatus.ACTIVE, "9912", 0), amount(5_000), null));
        assertThat(r.decision()).isEqualTo(FraudDecision.APPROVE);
        assertThat(r.totalScore()).isZero();
    }

    @Test
    @DisplayName("SINGLE_AMOUNT_LIMIT over the default threshold scores its weight → STEP_UP")
    void singleAmountLimitStepUp() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("SINGLE_AMOUNT_LIMIT", 35, Map.of("default_threshold_minor_units", 100_000))));
        FraudEvaluationResult r = engine.evaluate(ctx(card(CardStatus.ACTIVE, "9912", 0), amount(200_000), null));
        assertThat(r.totalScore()).isEqualTo(35);
        assertThat(r.decision()).isEqualTo(FraudDecision.STEP_UP);
    }

    @Test
    @DisplayName("SINGLE_AMOUNT_LIMIT uses the per-currency threshold map (ISO 4217 numeric)")
    void perCurrencyThreshold() {
        FraudRuleEntity r = rule("SINGLE_AMOUNT_LIMIT", 35,
                Map.of("thresholds", Map.of("840", 100_000)));
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(r));

        // 840 (USD) threshold = 100,000 minor units
        assertThat(engine.evaluate(ctx(card(CardStatus.ACTIVE, "9912", 0), amount(150_000), "840")).totalScore())
                .as("over USD threshold").isEqualTo(35);
        assertThat(engine.evaluate(ctx(card(CardStatus.ACTIVE, "9912", 0), amount(50_000), "840")).totalScore())
                .as("under USD threshold").isZero();
    }

    @Test
    @DisplayName("VELOCITY_LIMIT fires when recent approved count reaches the max")
    void velocityLimitFires() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("VELOCITY_LIMIT", 40, Map.of("max_transactions", 5, "window_minutes", 10))));
        when(authLogRepository.countApprovedSince(any(UUID.class), any(OffsetDateTime.class))).thenReturn(5L);

        FraudEvaluationResult r = engine.evaluate(ctx(card(CardStatus.ACTIVE, "9912", 0), amount(1_000), null));
        assertThat(r.totalScore()).isEqualTo(40);
        assertThat(r.decision()).isEqualTo(FraudDecision.STEP_UP);
    }

    @Test
    @DisplayName("combined rule scores cross the decline threshold → DECLINE")
    void combinedScoreDeclines() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("SINGLE_AMOUNT_LIMIT", 35, Map.of("default_threshold_minor_units", 100_000)),
                rule("VELOCITY_LIMIT", 40, Map.of("max_transactions", 5))));
        when(authLogRepository.countApprovedSince(any(UUID.class), any(OffsetDateTime.class))).thenReturn(5L);

        FraudEvaluationResult r = engine.evaluate(ctx(card(CardStatus.ACTIVE, "9912", 0), amount(200_000), null));
        assertThat(r.totalScore()).isEqualTo(75); // 35 + 40
        assertThat(r.decision()).isEqualTo(FraudDecision.DECLINE);
    }

    @Test
    @DisplayName("CNP_DEBIT contributes its weight for a card-not-present debit")
    void cnpDebitContributes() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule("CNP_DEBIT", 25, Map.of())));
        FraudContext c = new FraudContext(card(CardStatus.ACTIVE, "9912", 0), "411111****1111",
                amount(1_000), null, "000000", "T1", "M1", "Shop", "5999", "CNP", "000001",
                false, true, false, Map.of());
        FraudEvaluationResult r = engine.evaluate(c);
        assertThat(r.totalScore()).isEqualTo(25);
        assertThat(r.decision()).isEqualTo(FraudDecision.APPROVE); // 25 < 30 threshold
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Card card(CardStatus status, String expiryYyMm, int pinRetry) {
        Card c = new Card();
        c.setId(UUID.randomUUID());
        c.setStatus(status);
        c.setExpiryDate(expiryYyMm);
        c.setCardType(CardType.DEBIT);
        c.setPinRetryCount((short) pinRetry);
        return c;
    }

    private static BigDecimal amount(long minorUnits) {
        return BigDecimal.valueOf(minorUnits);
    }

    private static FraudContext ctx(Card card, BigDecimal amount, String currencyCode) {
        return new FraudContext(card, "411111****1111", amount, currencyCode, "000000",
                "TERM0001", "MERCH0001", "Test Merchant", "5411", "CHIP", "000001",
                true, true, false, Map.of());
    }

    private static FraudRuleEntity rule(String ruleId, int weight, Map<String, Object> params) {
        FraudRuleEntity e = new FraudRuleEntity();
        e.setRuleId(ruleId);
        e.setWeight(weight);
        e.setEnabled(true);
        e.setParams(params);
        return e;
    }
}

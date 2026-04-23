package com.cba.fraud;

import com.cba.common.exception.CbaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudEngineService — unit tests")
class FraudEngineServiceTest {

    @Mock FraudRuleRepository fraudRuleRepository;
    @Mock FraudAlertRepository alertRepository;
    @Mock BlacklistEntryRepository blacklistRepository;
    @Mock CustomerRiskScoreRepository riskScoreRepository;
    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks FraudEngineService fraudEngineService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void injectObjectMapper() throws Exception {
        // inject real ObjectMapper via reflection
        var field = FraudEngineService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(fraudEngineService, objectMapper);
    }

    private FraudRule buildRule(String type, boolean blocking, String params) {
        FraudRule rule = new FraudRule();
        rule.setId(UUID.randomUUID());
        rule.setName(type + "_RULE");
        rule.setRuleType(type);
        rule.setEnabled(true);
        rule.setBlocking(blocking);
        rule.setParams(params);
        return rule;
    }

    @Nested
    @DisplayName("preTransactionCheck")
    class PreTransactionCheck {

        @Test
        @DisplayName("passes when no blocking rules exist")
        void noBlockingRules_passes() {
            when(fraudRuleRepository.findByEnabledTrueAndBlockingTrueOrderByNameAsc())
                .thenReturn(List.of());
            when(fraudRuleRepository.findByEnabledTrueOrderByNameAsc()).thenReturn(List.of());

            assertThatCode(() -> fraudEngineService.preTransactionCheck(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), "USD", "DEBIT"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws when velocity limit exceeded")
        void velocityLimit_exceeded_throws() {
            FraudRule rule = buildRule("VELOCITY_LIMIT", true,
                "{\"max_count\":3,\"window_minutes\":60}");
            when(fraudRuleRepository.findByEnabledTrueAndBlockingTrueOrderByNameAsc())
                .thenReturn(List.of(rule));
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(UUID.class)))
                .thenReturn(5L); // exceeds max_count=3
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> fraudEngineService.preTransactionCheck(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), "USD", "DEBIT"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("velocity limit");
        }

        @Test
        @DisplayName("passes when velocity count below limit")
        void velocityLimit_notExceeded_passes() {
            FraudRule rule = buildRule("VELOCITY_LIMIT", true,
                "{\"max_count\":10,\"window_minutes\":60}");
            when(fraudRuleRepository.findByEnabledTrueAndBlockingTrueOrderByNameAsc())
                .thenReturn(List.of(rule));
            when(fraudRuleRepository.findByEnabledTrueOrderByNameAsc()).thenReturn(List.of());
            when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(UUID.class)))
                .thenReturn(2L);

            assertThatCode(() -> fraudEngineService.preTransactionCheck(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), "USD", "DEBIT"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws when customer is blacklisted")
        void blacklistHit_throws() {
            UUID customerId = UUID.randomUUID();
            FraudRule rule = buildRule("BLACKLIST_HIT", true, "{}");
            when(fraudRuleRepository.findByEnabledTrueAndBlockingTrueOrderByNameAsc())
                .thenReturn(List.of(rule));
            when(jdbcTemplate.queryForList(anyString(), eq(customerId)))
                .thenReturn(List.of(java.util.Map.of("national_id_encrypted", "enc", "first_name_encrypted", "enc")));
            BlacklistEntry entry = new BlacklistEntry();
            entry.setId(UUID.randomUUID());
            when(blacklistRepository.findActiveByTypeAndValue(eq("CUSTOMER"), eq(customerId.toString()), any(Instant.class)))
                .thenReturn(List.of(entry));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> fraudEngineService.preTransactionCheck(
                customerId, UUID.randomUUID(),
                new BigDecimal("100.00"), "USD", "DEBIT"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("sanctions list");
        }

        @Test
        @DisplayName("non-blocking large-cash rule raises alert but does not throw")
        void largeCash_nonBlocking_raisesAlert() {
            FraudRule rule = buildRule("LARGE_CASH_TRANSACTION", false, "{}");
            when(fraudRuleRepository.findByEnabledTrueAndBlockingTrueOrderByNameAsc())
                .thenReturn(List.of());
            when(fraudRuleRepository.findByEnabledTrueOrderByNameAsc()).thenReturn(List.of(rule));
            when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // 10000.00 * 100 = 1,000,000 which equals default threshold
            assertThatCode(() -> fraudEngineService.preTransactionCheck(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10000.00"), "USD", "CASH_IN"))
                .doesNotThrowAnyException();
            verify(alertRepository, atLeastOnce()).save(any());
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklisted {

        @Test
        @DisplayName("returns false when customer not found")
        void customerNotFound_returnsFalse() {
            UUID customerId = UUID.randomUUID();
            when(jdbcTemplate.queryForList(anyString(), eq(customerId))).thenReturn(List.of());

            assertThat(fraudEngineService.isBlacklisted(customerId)).isFalse();
        }

        @Test
        @DisplayName("returns false when no blacklist entry")
        void noBlacklistEntry_returnsFalse() {
            UUID customerId = UUID.randomUUID();
            when(jdbcTemplate.queryForList(anyString(), eq(customerId)))
                .thenReturn(List.of(java.util.Map.of("national_id_encrypted", "enc", "first_name_encrypted", "enc")));
            when(blacklistRepository.findActiveByTypeAndValue(anyString(), anyString(), any())).thenReturn(List.of());

            assertThat(fraudEngineService.isBlacklisted(customerId)).isFalse();
        }

        @Test
        @DisplayName("returns true when blacklist entry found")
        void blacklistEntryFound_returnsTrue() {
            UUID customerId = UUID.randomUUID();
            when(jdbcTemplate.queryForList(anyString(), eq(customerId)))
                .thenReturn(List.of(java.util.Map.of("national_id_encrypted", "enc", "first_name_encrypted", "enc")));
            BlacklistEntry entry = new BlacklistEntry();
            entry.setId(UUID.randomUUID());
            when(blacklistRepository.findActiveByTypeAndValue(eq("CUSTOMER"), eq(customerId.toString()), any()))
                .thenReturn(List.of(entry));

            assertThat(fraudEngineService.isBlacklisted(customerId)).isTrue();
        }
    }

    @Nested
    @DisplayName("isValueBlacklisted")
    class IsValueBlacklisted {

        @Test
        @DisplayName("returns false when no match")
        void noMatch_returnsFalse() {
            when(blacklistRepository.findActiveByTypeAndValue(anyString(), anyString(), any()))
                .thenReturn(List.of());
            assertThat(fraudEngineService.isValueBlacklisted("IP", "1.2.3.4")).isFalse();
        }

        @Test
        @DisplayName("returns true when match found")
        void match_returnsTrue() {
            BlacklistEntry entry = new BlacklistEntry();
            entry.setId(UUID.randomUUID());
            when(blacklistRepository.findActiveByTypeAndValue(anyString(), anyString(), any()))
                .thenReturn(List.of(entry));
            assertThat(fraudEngineService.isValueBlacklisted("IP", "10.0.0.1")).isTrue();
        }
    }

    @Nested
    @DisplayName("recalculateRiskScore")
    class RecalculateRiskScore {

        @Test
        @DisplayName("creates new risk score when none exists")
        void newScore_created() {
            UUID customerId = UUID.randomUUID();
            when(riskScoreRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
            when(alertRepository.countByCustomerIdAndStatus(customerId, "OPEN")).thenReturn(2L);
            when(alertRepository.countByCustomerIdAndStatus(customerId, "CLOSED_CONFIRMED")).thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), eq(customerId))).thenReturn(List.of());
            when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            fraudEngineService.recalculateRiskScore(customerId);

            verify(riskScoreRepository).save(argThat(s -> s.getScore() == 20 && "LOW".equals(s.getRiskLevel())));
        }

        @Test
        @DisplayName("updates existing risk score")
        void existingScore_updated() {
            UUID customerId = UUID.randomUUID();
            CustomerRiskScore existing = new CustomerRiskScore();
            existing.setCustomerId(customerId);
            when(riskScoreRepository.findByCustomerId(customerId)).thenReturn(Optional.of(existing));
            when(alertRepository.countByCustomerIdAndStatus(customerId, "OPEN")).thenReturn(0L);
            when(alertRepository.countByCustomerIdAndStatus(customerId, "CLOSED_CONFIRMED")).thenReturn(0L);
            when(jdbcTemplate.queryForList(anyString(), eq(customerId))).thenReturn(List.of());
            when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            fraudEngineService.recalculateRiskScore(customerId);

            verify(riskScoreRepository).save(argThat(s -> "LOW".equals(s.getRiskLevel())));
        }

        @Test
        @DisplayName("score capped at 100")
        void score_cappedAt100() {
            UUID customerId = UUID.randomUUID();
            when(riskScoreRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
            when(alertRepository.countByCustomerIdAndStatus(customerId, "OPEN")).thenReturn(10L); // 100
            when(alertRepository.countByCustomerIdAndStatus(customerId, "CLOSED_CONFIRMED")).thenReturn(5L); // 125
            when(jdbcTemplate.queryForList(anyString(), eq(customerId))).thenReturn(List.of());
            when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            fraudEngineService.recalculateRiskScore(customerId);

            verify(riskScoreRepository).save(argThat(s -> s.getScore() == 100));
        }
    }
}

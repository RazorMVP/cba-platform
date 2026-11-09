package com.cba.cardsettlement;

import com.cba.account.AccountGlPosting;
import com.cba.audit.AuditLogService;
import com.cba.cardsettlement.dto.CardSchemeSettlementRequest;
import com.cba.cardsettlement.dto.CardSchemeSettlementResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The tolerance decision on a scheme settlement's difference (backlog 15d, decision D7a). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardSchemeSettlementService — difference tolerance")
class CardSchemeSettlementServiceTest {

    @Mock CardSchemeSettlementRepository repository;
    @Mock AccountGlPosting accountGlPosting;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock AuditLogService auditLogService;

    CardSettlementProperties properties = new CardSettlementProperties();
    CardSchemeSettlementService service;

    @BeforeEach
    void setUp() {
        service = new CardSchemeSettlementService(repository, accountGlPosting, jdbcTemplate, auditLogService, properties);
        when(repository.findByReference(anyString())).thenReturn(Optional.empty());
        when(accountGlPosting.postCardSchemeSettlement(any(), any(), anyString(), any(), anyString(), any(), anyString()))
                .thenReturn("GL-1");
    }

    private CardSchemeSettlementResult record(String currency, String amount, String expected) {
        return service.record(new CardSchemeSettlementRequest("CSR-1", "VISA", LocalDate.now().minusDays(1), currency,
                new BigDecimal(amount), expected == null ? null : new BigDecimal(expected)), "svc");
    }

    @Test
    @DisplayName("no expectedAmount: expected = reported, posted as before")
    void noExpected() {
        CardSchemeSettlementResult r = record("USD", "10.00", null);

        assertThat(r.approved()).isTrue();
        assertThat(r.difference()).isEqualByComparingTo("0");
        verify(accountGlPosting).postCardSchemeSettlement(eq(new BigDecimal("10.00")), eq(new BigDecimal("10.00")),
                eq("USD"), any(), anyString(), any(), eq("CSR-1"));
    }

    @Test
    @DisplayName("nothing configured: a one-cent difference is over the tolerance (0)")
    void defaultZero() {
        CardSchemeSettlementResult r = record("USD", "10.01", "10.00");

        assertThat(r.approved()).isFalse();
        assertThat(r.reason()).isEqualTo("DIFFERENCE_OVER_TOLERANCE");
        assertThat(r.difference()).isEqualByComparingTo("0.01");
        verify(repository, never()).save(any());
        verify(accountGlPosting, never()).postCardSchemeSettlement(any(), any(), anyString(), any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("within the currency's tolerance (|difference| equal to it included): posted, difference stored")
    void withinTolerance() {
        properties.setDifferenceTolerance(Map.of("usd", new BigDecimal("0.05")));
        CardSchemeSettlementResult r = record("USD", "9.95", "10.00");

        assertThat(r.approved()).isTrue();
        assertThat(r.difference()).isEqualByComparingTo("-0.05");
        ArgumentCaptor<CardSchemeSettlement> saved = ArgumentCaptor.forClass(CardSchemeSettlement.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExpectedAmount()).isEqualByComparingTo("10.00");
        assertThat(saved.getValue().getDifference()).isEqualByComparingTo("-0.05");
    }

    @Test
    @DisplayName("the default key covers other currencies; a currency's own entry wins over it")
    void defaultKey() {
        properties.setDifferenceTolerance(Map.of("default", new BigDecimal("1"), "USD", BigDecimal.ZERO));

        assertThat(record("NGN", "100", "99.50").approved()).isTrue();
        assertThat(record("USD", "10.01", "10.00").reason()).isEqualTo("DIFFERENCE_OVER_TOLERANCE");
    }

    @Test
    @DisplayName("a negative tolerance counts as zero")
    void negativeTolerance() {
        properties.setDifferenceTolerance(Map.of("USD", new BigDecimal("-1")));
        assertThat(properties.toleranceFor("USD")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an expectedAmount with more decimals than the currency has is INVALID_AMOUNT")
    void expectedScale() {
        properties.setDifferenceTolerance(Map.of("USD", BigDecimal.ONE));
        assertThat(record("USD", "10.00", "10.001").reason()).isEqualTo("INVALID_AMOUNT");
    }
}

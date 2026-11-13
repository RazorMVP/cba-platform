package com.cba.card.settlement;

import com.cba.card.auth.CardAuthorizationService.HoldReleaseAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReconciliationService — what a backend answer resolves a hold as")
class ReconciliationServiceTest {

    @Test
    @DisplayName("released / refunded / never seen / already inactive resolve; any other refusal doesn't")
    void resolutionMapping() {
        assertThat(ReconciliationService.resolution(new HoldReleaseAnswer(true, true, "RELEASED", null))).isEqualTo("RELEASED");
        assertThat(ReconciliationService.resolution(new HoldReleaseAnswer(true, true, "REFUNDED", null))).isEqualTo("REFUNDED");
        assertThat(ReconciliationService.resolution(new HoldReleaseAnswer(true, false, null, "HOLD_NOT_FOUND")))
                .isEqualTo("NOT_FOUND_TOMBSTONED");
        assertThat(ReconciliationService.resolution(new HoldReleaseAnswer(true, false, "EXPIRED", "HOLD_NOT_ACTIVE")))
                .isEqualTo("ALREADY_INACTIVE");
        assertThat(ReconciliationService.resolution(new HoldReleaseAnswer(true, false, "CAPTURED", "ACCOUNT_CLOSED"))).isNull();
        assertThat(ReconciliationService.resolution(new HoldReleaseAnswer(true, false, "REFUNDED", "ALREADY_REFUNDED"))).isNull();
        assertThat(ReconciliationService.resolution(new HoldReleaseAnswer(true, false, null, null))).isNull();
    }

    @Test
    @DisplayName("amounts are shown in major units with the alphabetic code; an unknown code passes through")
    void money() {
        assertThat(ReconciliationService.Money.of(new BigDecimal("2500.0000"), "840").amount()).isEqualByComparingTo("25.00");
        assertThat(ReconciliationService.Money.of(new BigDecimal("2500.0000"), "840").currency()).isEqualTo("USD");
        assertThat(ReconciliationService.Money.of(new BigDecimal("500"), "392").amount()).isEqualByComparingTo("500");
        assertThat(ReconciliationService.Money.of(new BigDecimal("12"), "001").currency()).isEqualTo("001");
    }
}

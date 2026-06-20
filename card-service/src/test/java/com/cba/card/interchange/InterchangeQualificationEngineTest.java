package com.cba.card.interchange;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.card.Card;
import com.cba.card.card.CardRepository;
import com.cba.card.card.CardType;
import com.cba.card.common.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InterchangeQualificationEngine} — the settlement math that
 * computes interchange + scheme fees and nets them against the gross amount.
 * Wrong numbers here corrupt scheme settlement figures.
 */
@ExtendWith(MockitoExtension.class)
class InterchangeQualificationEngineTest {

    @Mock InterchangeRateRepository rateRepo;
    @Mock SchemeFeeRepository feeRepo;
    @Mock InterchangeLogRepository logRepo;
    @Mock CardRepository cardRepo;
    @Mock AuthorizationLogRepository authRepo;

    @InjectMocks InterchangeQualificationEngine engine;

    private static AuthorizationLog auth(BigDecimal amount, String scheme, UUID cardId) {
        AuthorizationLog a = new AuthorizationLog();
        a.setId(UUID.randomUUID());
        a.setAmount(amount);
        a.setScheme(scheme);
        a.setCardId(cardId);
        a.setEntryMode("CHIP");
        a.setProcessingCode("000000");
        a.setMcc("5411");
        return a;
    }

    private static InterchangeRate rate(String percent, String fixed) {
        InterchangeRate r = new InterchangeRate();
        r.setScheme("VISA");
        r.setCardType(CardType.CREDIT);
        r.setTransactionType(TransactionType.PURCHASE);
        r.setChannel(ChannelType.CARD_PRESENT);
        r.setRatePercent(new BigDecimal(percent));
        r.setFixedFee(new BigDecimal(fixed));
        return r;
    }

    private static SchemeFee fee(String percent, String fixed) {
        SchemeFee f = new SchemeFee();
        f.setScheme("VISA");
        f.setRatePercent(new BigDecimal(percent));
        f.setFixedFee(new BigDecimal(fixed));
        return f;
    }

    @Test
    @DisplayName("a zero-amount auth yields a no-rate result (interchange 0, net 0)")
    void zeroAmount() {
        InterchangeResult r = engine.calculate(auth(BigDecimal.ZERO, "VISA", null));
        assertThat(r.interchangeAmount()).isEqualByComparingTo("0");
        assertThat(r.netSettlementAmount()).isEqualByComparingTo("0");
        assertThat(r.rateApplied()).isEqualTo("NO_RATE_CONFIGURED");
    }

    @Test
    @DisplayName("interchange = gross×rate% + fixed; net = gross − interchange − schemeFees")
    void rateAndFeesNetting() {
        UUID cardId = UUID.randomUUID();
        Card card = new Card(); card.setCardType(CardType.CREDIT);
        when(cardRepo.findById(cardId)).thenReturn(Optional.of(card));
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(rate("1.5", "10")));      // 1.5% + 10
        when(feeRepo.findActiveByScheme(any(), any()))
                .thenReturn(List.of(fee("0.13", "0")));        // 0.13%

        // scheme deliberately lowercase to exercise toUpperCase()
        InterchangeResult r = engine.calculate(auth(new BigDecimal("10000"), "visa", cardId));

        assertThat(r.interchangeAmount()).isEqualByComparingTo("160.0000");  // 150 + 10
        assertThat(r.schemeFeeAmount()).isEqualByComparingTo("13.0000");     // 13
        assertThat(r.netSettlementAmount()).isEqualByComparingTo("9827.0000"); // 10000 - 160 - 13
    }

    @Test
    @DisplayName("no configured rate → interchange 0, net = gross − schemeFees")
    void noRateConfigured() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of());

        InterchangeResult r = engine.calculate(auth(new BigDecimal("5000"), "VISA", null));

        assertThat(r.interchangeAmount()).isEqualByComparingTo("0");
        assertThat(r.netSettlementAmount()).isEqualByComparingTo("5000.0000");
        assertThat(r.rateApplied()).isEqualTo("NO_RATE_CONFIGURED");
    }

    @Test
    @DisplayName("calculateForAuth throws when the authorization log is not found")
    void calculateForAuthNotFound() {
        UUID id = UUID.randomUUID();
        when(authRepo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> engine.calculateForAuth(id)).isInstanceOf(CbaException.class);
    }
}

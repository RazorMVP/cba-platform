package com.cba.card.interchange;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.card.CardRepository;
import com.cba.card.card.CardType;
import com.cba.card.common.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InterchangeQualificationEngine} from the issuer's side (backlog 15b): what the bank earns on a
 * purchase, pays on ATM cash, and pays the scheme in fees. Wrong numbers here post wrong income and a
 * wrong scheme settlement.
 */
@ExtendWith(MockitoExtension.class)
class InterchangeQualificationEngineTest {

    static final Currency USD = Currency.getInstance("USD");

    @Mock InterchangeRateRepository rateRepo;
    @Mock SchemeFeeRepository feeRepo;
    @Mock InterchangeLogRepository logRepo;
    @Mock CardRepository cardRepo;
    @Mock AuthorizationLogRepository authRepo;

    @InjectMocks InterchangeQualificationEngine engine;

    private static InterchangeRate rate(String percent, String fixed, String currency) {
        InterchangeRate r = new InterchangeRate();
        r.setScheme("VISA");
        r.setCardType(CardType.DEBIT);
        r.setTransactionType(TransactionType.PURCHASE);
        r.setChannel(ChannelType.CARD_PRESENT);
        r.setRatePercent(new BigDecimal(percent));
        r.setFixedFee(new BigDecimal(fixed));
        r.setCurrencyCode(currency);
        return r;
    }

    private static SchemeFee fee(SchemeFeeType type, String percent, String fixed, String currency) {
        SchemeFee f = new SchemeFee();
        f.setScheme("VISA");
        f.setFeeType(type);
        f.setRatePercent(new BigDecimal(percent));
        f.setFixedFee(new BigDecimal(fixed));
        f.setCurrencyCode(currency);
        return f;
    }

    /** Visa: interchange 1.50 %, fees 0.11 % assessment + 0.0195 network (USD). */
    private void visaRates() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of(rate("1.5", "0", "USD")));
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of(
                fee(SchemeFeeType.ASSESSMENT, "0.11", "0", "USD"), fee(SchemeFeeType.NETWORK, "0", "0.0195", "USD")));
    }

    @Test
    @DisplayName("purchase: the issuer EARNS interchange (1.50 on 100.00) and pays 0.13 in fees (0.1295 rounded once)")
    void purchase_issuerEarns() {
        visaRates();
        IssuerCharges c = engine.quote("VISA", CardType.DEBIT, "000000", "051", "5999", new BigDecimal("100.00"), USD);
        assertThat(c.interchange()).isEqualByComparingTo("1.50");
        assertThat(c.direction()).isEqualTo(InterchangeDirection.EARNED);
        assertThat(c.schemeFee()).isEqualByComparingTo("0.13");
        assertThat(c.owedToScheme(new BigDecimal("100.00"))).isEqualByComparingTo("98.63");
    }

    @Test
    @DisplayName("ATM cash: the issuer PAYS interchange; it owes the scheme the cash plus interchange plus fees")
    void cash_issuerPays() {
        visaRates();
        IssuerCharges c = engine.quote("VISA", CardType.DEBIT, "010000", "051", "6011", new BigDecimal("50.00"), USD);
        assertThat(c.direction()).isEqualTo(InterchangeDirection.PAID);
        assertThat(c.interchange()).isEqualByComparingTo("0.75");
        assertThat(c.owedToScheme(new BigDecimal("50.00"))).isEqualByComparingTo(new BigDecimal("50.00").add(c.interchange()).add(c.schemeFee()));
    }

    @Test
    @DisplayName("refunds, prepaid cards and a zero amount carry no interchange or fees")
    void nothingApplies() {
        assertThat(engine.quote("VISA", CardType.DEBIT, "200000", "051", null, BigDecimal.TEN, USD).interchange()).isZero();
        assertThat(engine.quote("VISA", CardType.PREPAID, "000000", "051", null, BigDecimal.TEN, USD).schemeFee()).isZero();
        assertThat(engine.quote("VISA", CardType.DEBIT, "000000", "051", null, BigDecimal.ZERO, USD).rateApplied()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("fixed fees are major units of the row's currency: 0.90 % + 0.05 USD on 100.00 USD = 0.95")
    void fixedFee_majorUnits_sameCurrency() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of(rate("0.9", "0.05", "USD")));
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of());
        assertThat(engine.quote("VISA", CardType.DEBIT, "000000", "051", null, new BigDecimal("100.00"), USD).interchange())
                .isEqualByComparingTo("0.95");
    }

    @Test
    @DisplayName("a fixed fee in another currency is skipped (no FX here): only the percentage applies")
    void fixedFee_skipped_otherCurrency() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of(rate("1.0", "0.25", "USD")));
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of(fee(SchemeFeeType.NETWORK, "0", "0.0195", "USD")));
        IssuerCharges c = engine.quote("VISA", CardType.DEBIT, "000000", "051", null, new BigDecimal("1000.00"),
                Currency.getInstance("NGN"));
        assertThat(c.interchange()).isEqualByComparingTo("10.00");
        assertThat(c.schemeFee()).isZero();
        assertThat(c.rateApplied()).contains("fixed fee skipped");
    }

    @Test
    @DisplayName("JPY has no decimals: 1.50 % of 1000 = 15, fees 0.11 % = 1.1 → 1")
    void jpy_zeroDigits_rounding() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of(rate("1.5", "0", "JPY")));
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of(fee(SchemeFeeType.ASSESSMENT, "0.11", "0", "JPY")));
        IssuerCharges c = engine.quote("VISA", CardType.DEBIT, "000000", "051", null, new BigDecimal("1000"),
                Currency.getInstance("JPY"));
        assertThat(c.interchange()).isEqualByComparingTo("15");
        assertThat(c.schemeFee()).isEqualByComparingTo("1");
        assertThat(c.schemeFee().scale()).isZero();
    }

    @Test
    @DisplayName("only ASSESSMENT and NETWORK fees apply — cross-border needs the merchant's country (15d)")
    void onlyAssessmentAndNetwork() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of(
                fee(SchemeFeeType.ASSESSMENT, "0.11", "0", "USD"),
                fee(SchemeFeeType.CROSS_BORDER, "0.40", "0", "USD"),
                fee(SchemeFeeType.INTERNATIONAL_SERVICE, "0.90", "0", "USD")));
        assertThat(engine.quote("VISA", CardType.DEBIT, "000000", "051", null, new BigDecimal("100.00"), USD).schemeFee())
                .isEqualByComparingTo("0.11");
    }

    @Test
    @DisplayName("UNION_PAY and UNIONPAY both resolve to the canonical UNIONPAY rates")
    void unionPay_bothSpellingsResolve() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of());
        assertThat(engine.quote("union_pay", CardType.DEBIT, "000000", "051", null, BigDecimal.TEN, USD).scheme())
                .isEqualTo("UNIONPAY");
        verify(rateRepo).findBestMatch(eq("UNIONPAY"), any(), any(), any(), any(), any());
        assertThat(Schemes.normalize("UNIONPAY")).isEqualTo("UNIONPAY");
        assertThat(Schemes.normalize(null)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("no configured rate → interchange 0, fees still apply")
    void noRate_zeroInterchange_feesStillApply() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of(fee(SchemeFeeType.ASSESSMENT, "0.11", "0", "USD")));
        IssuerCharges c = engine.quote("VISA", CardType.DEBIT, "000000", "051", null, new BigDecimal("100.00"), USD);
        assertThat(c.interchange()).isZero();
        assertThat(c.schemeFee()).isEqualByComparingTo("0.11");
        assertThat(c.rateApplied()).isEqualTo("NO_RATE_CONFIGURED");
    }

    @Test
    @DisplayName("a repository failure never fails the transaction: no interchange, no fees")
    void repositoryThrows_returnsNone() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("db down"));
        IssuerCharges c = engine.quote("VISA", CardType.DEBIT, "000000", "051", null, new BigDecimal("100.00"), USD);
        assertThat(c.interchange()).isZero();
        assertThat(c.schemeFee()).isZero();
    }

    @Test
    @DisplayName("channel from DE22 codes (05x chip, 07x contactless, 02x/90x stripe = present; 01x, 81x = not) or names")
    void channelMapping() {
        assertThat(InterchangeQualificationEngine.mapChannel("051")).isEqualTo(ChannelType.CARD_PRESENT);
        assertThat(InterchangeQualificationEngine.mapChannel("071")).isEqualTo(ChannelType.CARD_PRESENT);
        assertThat(InterchangeQualificationEngine.mapChannel("901")).isEqualTo(ChannelType.CARD_PRESENT);
        assertThat(InterchangeQualificationEngine.mapChannel("CHIP")).isEqualTo(ChannelType.CARD_PRESENT);
        assertThat(InterchangeQualificationEngine.mapChannel("012")).isEqualTo(ChannelType.CNP);
        assertThat(InterchangeQualificationEngine.mapChannel("812")).isEqualTo(ChannelType.CNP);
        assertThat(InterchangeQualificationEngine.mapChannel(null)).isEqualTo(ChannelType.CNP);
    }

    @Test
    @DisplayName("diagnostic calculate: DE4 minor units → major; net = what the issuer owes the scheme; logged")
    void calculate_issuerView_recorded() {
        visaRates();
        AuthorizationLog a = new AuthorizationLog();
        a.setId(UUID.randomUUID());
        a.setAmount(new BigDecimal("10000"));   // 100.00 USD
        a.setCurrencyCode("840");
        a.setScheme("visa");
        a.setEntryMode("051");
        a.setProcessingCode("000000");

        InterchangeResult r = engine.calculate(a);

        assertThat(r.interchangeAmount()).isEqualByComparingTo("1.50");
        assertThat(r.direction()).isEqualTo(InterchangeDirection.EARNED);
        assertThat(r.netSettlementAmount()).isEqualByComparingTo("98.63");
        ArgumentCaptor<InterchangeLog> logged = ArgumentCaptor.forClass(InterchangeLog.class);
        verify(logRepo).save(logged.capture());
        assertThat(logged.getValue().getGrossAmount()).isEqualByComparingTo("100.00");
        assertThat(logged.getValue().getCurrencyCode()).isEqualTo("USD");
        assertThat(logged.getValue().getScheme()).isEqualTo("VISA");
    }

    @Test
    @DisplayName("calculateForAuth throws when the authorization log is not found")
    void calculateForAuthNotFound() {
        UUID id = UUID.randomUUID();
        when(authRepo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> engine.calculateForAuth(id)).isInstanceOf(CbaException.class);
    }
}

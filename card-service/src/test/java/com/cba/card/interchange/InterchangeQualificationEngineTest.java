package com.cba.card.interchange;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.auth.CardCountry;
import com.cba.card.bin.BinService;
import com.cba.card.card.Card;
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
    @Mock BinService binService;

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
    @DisplayName("refunds and a zero amount carry no interchange or fees")
    void nothingApplies() {
        assertThat(engine.quote("VISA", CardType.DEBIT, "200000", "051", null, BigDecimal.TEN, USD).interchange()).isZero();
        assertThat(engine.quote("VISA", CardType.DEBIT, "000000", "051", null, BigDecimal.ZERO, USD).rateApplied()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("prepaid earns interchange and pays fees like debit, now that prepaid spend posts to the GL (backlog 15c)")
    void prepaidEarns() {
        visaRates();
        IssuerCharges c = engine.quote("VISA", CardType.PREPAID, "000000", "051", "5999", new BigDecimal("100.00"), USD);
        assertThat(c.interchange()).isEqualByComparingTo("1.50");
        assertThat(c.direction()).isEqualTo(InterchangeDirection.EARNED);
        assertThat(c.schemeFee()).isEqualByComparingTo("0.13");
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

    // ── Cross-border and currency-conversion fees (backlog 15d, decision D2a) ────────────────────

    static final Currency EUR = Currency.getInstance("EUR");

    /** Visa seed figures (V3): assessment 0.11 %, cross-border 0.40 %, international service 0.90 %. */
    private void visaAllFees() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of(
                fee(SchemeFeeType.ASSESSMENT, "0.11", "0", "USD"),
                fee(SchemeFeeType.CROSS_BORDER, "0.40", "0", "USD"),
                fee(SchemeFeeType.INTERNATIONAL_SERVICE, "0.90", "0", "USD")));
    }

    private IssuerCharges purchase(CardCountry country, Currency transactionCurrency) {
        return engine.quote("VISA", CardType.DEBIT, "000000", "051", null, new BigDecimal("100.00"), USD,
                country, transactionCurrency);
    }

    @Test
    @DisplayName("domestic, card currency: assessment only — no cross-border or currency fee")
    void domestic_noExtraFees() {
        visaAllFees();
        IssuerCharges c = purchase(CardCountry.DOMESTIC, USD);
        assertThat(c.schemeFee()).isEqualByComparingTo("0.11");
        assertThat(c.crossBorderFee()).isZero();
        assertThat(c.currencyFee()).isZero();
        assertThat(c.countryUnknown()).isFalse();
    }

    @Test
    @DisplayName("the 7-argument quote is a domestic transaction in the card's currency")
    void legacyQuote_isDomestic() {
        visaAllFees();
        IssuerCharges c = engine.quote("VISA", CardType.DEBIT, "000000", "051", null, new BigDecimal("100.00"), USD);
        assertThat(c.schemeFee()).isEqualByComparingTo("0.11");
        assertThat(c.countryUnknown()).isFalse();
    }

    @Test
    @DisplayName("acquirer abroad, card currency: + 0.40 % cross-border, in the scheme fee total")
    void crossBorderOnly() {
        visaAllFees();
        IssuerCharges c = purchase(CardCountry.CROSS_BORDER, USD);
        assertThat(c.crossBorderFee()).isEqualByComparingTo("0.40");
        assertThat(c.currencyFee()).isZero();
        assertThat(c.schemeFee()).isEqualByComparingTo("0.51");
        assertThat(c.owedToScheme(new BigDecimal("100.00"))).isEqualByComparingTo("100.51");
    }

    @Test
    @DisplayName("domestic acquirer, transaction in another currency (DE49 ≠ DE51): + 0.90 % international service")
    void currencyOnly() {
        visaAllFees();
        IssuerCharges c = purchase(CardCountry.DOMESTIC, EUR);
        assertThat(c.crossBorderFee()).isZero();
        assertThat(c.currencyFee()).isEqualByComparingTo("0.90");
        assertThat(c.schemeFee()).isEqualByComparingTo("1.01");
    }

    @Test
    @DisplayName("abroad and in another currency: both fees, and the parts add up to the total")
    void crossBorderAndCurrency() {
        visaAllFees();
        IssuerCharges c = purchase(CardCountry.CROSS_BORDER, EUR);
        assertThat(c.crossBorderFee()).isEqualByComparingTo("0.40");
        assertThat(c.currencyFee()).isEqualByComparingTo("0.90");
        assertThat(c.schemeFee()).isEqualByComparingTo("1.41");
    }

    @Test
    @DisplayName("country unknown: no cross-border fee and the charges say so; the currency fee still applies on its own rule")
    void unknownCountry_noCrossBorder_flagged() {
        visaAllFees();
        IssuerCharges c = purchase(CardCountry.UNKNOWN, EUR);
        assertThat(c.crossBorderFee()).isZero();
        assertThat(c.currencyFee()).isEqualByComparingTo("0.90");
        assertThat(c.schemeFee()).isEqualByComparingTo("1.01");
        assertThat(c.countryUnknown()).isTrue();
        assertThat(purchase(null, USD).countryUnknown()).isTrue();
    }

    @Test
    @DisplayName("a refund carries no fees, abroad or not")
    void refund_stillNone() {
        IssuerCharges c = engine.quote("VISA", CardType.DEBIT, "200000", "051", null, new BigDecimal("100.00"), USD,
                CardCountry.CROSS_BORDER, EUR);
        assertThat(c.schemeFee()).isZero();
        assertThat(c.crossBorderFee()).isZero();
        assertThat(c.currencyFee()).isZero();
    }

    @Test
    @DisplayName("fixed parts of cross-border / currency fees still apply only in their own currency")
    void crossBorderFixedFee_currencyRule() {
        when(rateRepo.findBestMatch(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(feeRepo.findActiveByScheme(any(), any())).thenReturn(List.of(
                fee(SchemeFeeType.CROSS_BORDER, "0.10", "0.50", "USD"),
                fee(SchemeFeeType.INTERNATIONAL_SERVICE, "0", "1.00", "NGN")));
        IssuerCharges c = purchase(CardCountry.CROSS_BORDER, EUR);
        assertThat(c.crossBorderFee()).isEqualByComparingTo("0.60");   // 0.10 % of 100 + 0.50 USD
        assertThat(c.currencyFee()).isZero();                          // NGN fixed fee on a USD card amount: skipped
        assertThat(c.schemeFee()).isEqualByComparingTo("0.60");
    }

    @Test
    @DisplayName("countryOf: the card's BIN-range country against DE19; UNKNOWN without DE19, card or country")
    void countryOf() {
        Card card = new Card();
        card.setPanPrefix("41000000");
        UUID cardId = UUID.randomUUID();
        when(cardRepo.findById(cardId)).thenReturn(Optional.of(card));
        when(binService.issuingCountry("41000000")).thenReturn("USA");
        assertThat(engine.countryOf(cardId, "840")).isEqualTo(CardCountry.DOMESTIC);
        assertThat(engine.countryOf(cardId, "566")).isEqualTo(CardCountry.CROSS_BORDER);
        assertThat(engine.countryOf(cardId, "999")).isEqualTo(CardCountry.UNKNOWN);
        assertThat(engine.countryOf(cardId, null)).isEqualTo(CardCountry.UNKNOWN);
        assertThat(engine.countryOf(null, "840")).isEqualTo(CardCountry.UNKNOWN);
    }

    @Test
    @DisplayName("diagnostic calculate: a pre-V20 authorization (no DE19) reports country unknown and no cross-border fee")
    void calculate_noCountry_reportsUnknown() {
        visaAllFees();
        AuthorizationLog a = new AuthorizationLog();
        a.setId(UUID.randomUUID());
        a.setAmount(new BigDecimal("10000"));
        a.setCurrencyCode("840");
        a.setScheme("VISA");
        a.setProcessingCode("000000");
        a.setCardId(UUID.randomUUID());

        InterchangeResult r = engine.calculate(a);

        assertThat(r.countryUnknown()).isTrue();
        assertThat(r.crossBorderFee()).isZero();
        assertThat(r.schemeFeeAmount()).isEqualByComparingTo("0.11");
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

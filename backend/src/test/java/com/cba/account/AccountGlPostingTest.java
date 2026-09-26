package com.cba.account;

import com.cba.accounting.FinancialActivityAccount.FinancialActivity;
import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountingService;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.accounting.JournalEntry;
import com.cba.common.exception.CbaException;
import com.cba.currency.ExchangeRate;
import com.cba.currency.ExchangeRateService;
import com.cba.product.DepositProduct;
import com.cba.system.GlobalConfiguration;
import com.cba.system.GlobalConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The accounting rules of deposit postings: which GL accounts move, on which side, in
 * which currency — and that every journal balances in every currency.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AccountGlPosting — journal lines for deposit balance changes")
class AccountGlPostingTest {

    @Mock GlAccountingService gl;
    @Mock GlobalConfigurationRepository globalConfigRepository;
    @Mock ExchangeRateService exchangeRateService;
    @InjectMocks AccountGlPosting posting;

    private final Map<FinancialActivity, GlAccount> mapped = new EnumMap<>(FinancialActivity.class);

    @BeforeEach
    void setUp() {
        for (FinancialActivity a : FinancialActivity.values()) {
            mapped.put(a, gl(a.name()));
        }
        when(gl.activityAccount(any())).thenAnswer(inv -> mapped.get(inv.<FinancialActivity>getArgument(0)));
        when(gl.postJournal(anyList(), any(), any(), any(), any(), any())).thenReturn("GL-1");
    }

    // ── Cash ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deposit: DR Cash at Teller / CR savings control")
    void deposit() {
        Account a = account("USD", "1100.00");
        posting.postCashMovement(a, new BigDecimal("1000.00"), LocalDate.now(), "d", "R1");

        assertThat(lines()).containsExactlyInAnyOrder(
            line(FinancialActivity.LIABILITY_SAVINGS_CONTROL, "CREDIT", "100.00", "USD"),
            line(FinancialActivity.ASSET_CASH_AT_TELLER, "DEBIT", "100.00", "USD"));
    }

    @Test
    @DisplayName("withdrawal that crosses zero: the part below zero is an overdraft asset, not a negative deposit")
    void withdrawalIntoOverdraft() {
        Account a = account("USD", "-30.00");
        posting.postCashMovement(a, new BigDecimal("50.00"), LocalDate.now(), "w", "R2");

        assertThat(lines()).containsExactlyInAnyOrder(
            line(FinancialActivity.LIABILITY_SAVINGS_CONTROL, "DEBIT", "50.00", "USD"),
            line(FinancialActivity.ASSET_OVERDRAFT_PORTFOLIO, "DEBIT", "30.00", "USD"),
            line(FinancialActivity.ASSET_CASH_AT_TELLER, "CREDIT", "80.00", "USD"));
    }

    @Test
    @DisplayName("deposit into an overdrawn account clears the overdraft first")
    void depositClearsOverdraft() {
        Account a = account("USD", "20.00");
        posting.postCashMovement(a, new BigDecimal("-30.00"), LocalDate.now(), "d", "R3");

        assertThat(lines()).containsExactlyInAnyOrder(
            line(FinancialActivity.ASSET_OVERDRAFT_PORTFOLIO, "CREDIT", "30.00", "USD"),
            line(FinancialActivity.LIABILITY_SAVINGS_CONTROL, "CREDIT", "20.00", "USD"),
            line(FinancialActivity.ASSET_CASH_AT_TELLER, "DEBIT", "50.00", "USD"));
    }

    @Test
    @DisplayName("the deposit product's own GL link wins over the activity mapping")
    void productLinkWins() {
        Account a = account("USD", "110.00");
        GlAccount productControl = gl("PRODUCT_CONTROL");
        a.getProduct().setSavingsControlAccount(productControl);

        posting.postCashMovement(a, new BigDecimal("100.00"), LocalDate.now(), "d", "R4");

        assertThat(lines()).anySatisfy(l -> {
            assertThat(l.account()).isSameAs(productControl);
            assertThat(l.side()).isEqualTo(JournalEntry.EntryType.CREDIT);
        });
        verify(gl, never()).activityAccount(FinancialActivity.LIABILITY_SAVINGS_CONTROL);
    }

    @Test
    @DisplayName("an unmapped activity rejects the posting (the caller's transaction rolls back)")
    void unmappedRejects() {
        when(gl.activityAccount(FinancialActivity.ASSET_CASH_AT_TELLER))
            .thenThrow(CbaException.badRequest("ACTIVITY_NOT_MAPPED", "unmapped"));
        Account a = account("USD", "110.00");

        assertThatThrownBy(() -> posting.postCashMovement(a, new BigDecimal("100.00"), LocalDate.now(), "d", "R"))
            .isInstanceOf(CbaException.class).hasMessageContaining("unmapped");
        verify(gl, never()).postJournal(anyList(), any(), any(), any(), any(), any());
    }

    // ── Interest ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("interest credit: DR interest on savings / CR savings control")
    void interest() {
        Account a = account("USD", "1000.1370");
        posting.postInterestCredit(a, new BigDecimal("1000.0000"), LocalDate.now(), "i", "INT-1");

        assertThat(lines()).containsExactlyInAnyOrder(
            line(FinancialActivity.LIABILITY_SAVINGS_CONTROL, "CREDIT", "0.1370", "USD"),
            line(FinancialActivity.EXPENSE_INTEREST_ON_SAVINGS, "DEBIT", "0.1370", "USD"));
    }

    // ── Transfers ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("same-currency transfer: the two balance changes offset; no FX lines")
    void sameCurrencyTransfer() {
        Account src = account("USD", "800.00");
        Account dst = account("USD", "700.00");
        posting.postTransfer(src, new BigDecimal("1000.00"), dst, new BigDecimal("500.00"),
            LocalDate.now(), "t", "PAY-1", UUID.randomUUID());

        assertThat(lines()).containsExactlyInAnyOrder(
            line(FinancialActivity.LIABILITY_SAVINGS_CONTROL, "DEBIT", "200.00", "USD"),
            line(FinancialActivity.LIABILITY_SAVINGS_CONTROL, "CREDIT", "200.00", "USD"));
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("cross-currency from the functional currency: KES leg via FX position, residual to FX gain/loss")
    void crossCurrencyFromFunctional() {
        functionalCurrency("USD");
        rate("KES", "USD", "0.0074"); // 13,550 KES = 100.27 USD at spot
        Account src = account("USD", "900.00");
        Account dst = account("KES", "13550.00");

        posting.postTransfer(src, new BigDecimal("1000.00"), dst, BigDecimal.ZERO,
            LocalDate.now(), "fx", "PAY-2", UUID.randomUUID());

        assertThat(lines()).containsExactlyInAnyOrder(
            line(FinancialActivity.LIABILITY_SAVINGS_CONTROL, "DEBIT", "100.00", "USD"),
            line(FinancialActivity.LIABILITY_SAVINGS_CONTROL, "CREDIT", "13550.00", "KES"),
            line(FinancialActivity.ASSET_FX_POSITION, "DEBIT", "13550.00", "KES"),
            line(FinancialActivity.ASSET_FX_POSITION_EQUIVALENT, "CREDIT", "100.2700", "USD"),
            // The bank now owes KES worth 100.27 USD for 100 USD received: a 0.27 loss.
            line(FinancialActivity.INCOME_FX_GAIN_LOSS, "DEBIT", "0.2700", "USD"));
    }

    @Test
    @DisplayName("cross-currency between two foreign currencies: both legs through positions")
    void crossCurrencyBothForeign() {
        functionalCurrency("USD");
        rate("KES", "USD", "0.0074");
        rate("GHS", "USD", "0.0800");
        Account src = account("KES", "0.00");
        Account dst = account("GHS", "1250.00");

        posting.postTransfer(src, new BigDecimal("13550.00"), dst, BigDecimal.ZERO,
            LocalDate.now(), "fx", "PAY-3", UUID.randomUUID());

        List<JournalLine> lines = lines();
        assertThat(lines).contains(
            line(FinancialActivity.ASSET_FX_POSITION, "CREDIT", "13550.00", "KES"),
            line(FinancialActivity.ASSET_FX_POSITION_EQUIVALENT, "DEBIT", "100.2700", "USD"),
            line(FinancialActivity.ASSET_FX_POSITION, "DEBIT", "1250.00", "GHS"),
            line(FinancialActivity.ASSET_FX_POSITION_EQUIVALENT, "CREDIT", "100.0000", "USD"),
            line(FinancialActivity.INCOME_FX_GAIN_LOSS, "CREDIT", "0.2700", "USD"));
    }

    @Test
    @DisplayName("cross-currency without a configured functional currency is rejected")
    void crossCurrencyWithoutFunctionalCurrency() {
        when(globalConfigRepository.findByName("functional-currency")).thenReturn(Optional.empty());
        Account src = account("USD", "900.00");
        Account dst = account("KES", "13550.00");

        assertThatThrownBy(() -> posting.postTransfer(src, new BigDecimal("1000.00"), dst, BigDecimal.ZERO,
                LocalDate.now(), "fx", "PAY-4", UUID.randomUUID()))
            .isInstanceOf(CbaException.class).hasMessageContaining("functional-currency");
    }

    // ── Teller count ────────────────────────────────────────────────────────

    @Test
    @DisplayName("cash short at close: DR Cash Over and Short / CR Cash at Teller")
    void cashShort() {
        posting.postCashCountDifference(new BigDecimal("-10.00"), "USD", LocalDate.now(), "short", UUID.randomUUID());

        assertThat(lines()).containsExactlyInAnyOrder(
            line(FinancialActivity.EXPENSE_CASH_OVER_SHORT, "DEBIT", "10.00", "USD"),
            line(FinancialActivity.ASSET_CASH_AT_TELLER, "CREDIT", "10.00", "USD"));
    }

    @Test
    @DisplayName("cash over at close: DR Cash at Teller / CR Cash Over and Short")
    void cashOver() {
        posting.postCashCountDifference(new BigDecimal("5.00"), "USD", LocalDate.now(), "over", UUID.randomUUID());

        assertThat(lines()).containsExactlyInAnyOrder(
            line(FinancialActivity.ASSET_CASH_AT_TELLER, "DEBIT", "5.00", "USD"),
            line(FinancialActivity.EXPENSE_CASH_OVER_SHORT, "CREDIT", "5.00", "USD"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** The posted lines; also asserts the journal balances in every currency. */
    @SuppressWarnings("unchecked")
    private List<JournalLine> lines() {
        ArgumentCaptor<List<JournalLine>> cap = ArgumentCaptor.forClass(List.class);
        verify(gl).postJournal(cap.capture(), any(), any(), any(), any(), any());
        List<JournalLine> lines = cap.getValue();
        Map<String, BigDecimal> net = new TreeMap<>();
        lines.forEach(l -> net.merge(l.currencyCode(),
            l.side() == JournalEntry.EntryType.DEBIT ? l.amount() : l.amount().negate(), BigDecimal::add));
        net.forEach((ccy, n) -> assertThat(n).as("net in " + ccy).isEqualByComparingTo("0"));
        return lines;
    }

    private JournalLine line(FinancialActivity activity, String side, String amount, String ccy) {
        return new JournalLine(mapped.get(activity), JournalEntry.EntryType.valueOf(side), new BigDecimal(amount), ccy);
    }

    private void functionalCurrency(String ccy) {
        GlobalConfiguration c = new GlobalConfiguration();
        c.setName("functional-currency");
        c.setStringValue(ccy);
        c.setEnabled(true);
        when(globalConfigRepository.findByName("functional-currency")).thenReturn(Optional.of(c));
    }

    private void rate(String from, String to, String rate) {
        ExchangeRate r = new ExchangeRate();
        r.setFromCurrency(from);
        r.setToCurrency(to);
        r.setRate(new BigDecimal(rate));
        when(exchangeRateService.getRate(from, to)).thenReturn(r);
    }

    private static GlAccount gl(String code) {
        GlAccount g = new GlAccount();
        g.setId(UUID.randomUUID());
        g.setGlCode(code);
        g.setName(code);
        return g;
    }

    private static Account account(String ccy, String balance) {
        Account a = new Account();
        a.setId(UUID.randomUUID());
        a.setAccountNumber("001-SAV-" + UUID.randomUUID().toString().substring(0, 6));
        a.setCurrencyCode(ccy);
        a.setBalance(new BigDecimal(balance));
        a.setProduct(new DepositProduct());
        return a;
    }
}

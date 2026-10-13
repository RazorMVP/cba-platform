package com.cba.loan;

import com.cba.accounting.FinancialActivityAccount.FinancialActivity;
import com.cba.accounting.FunctionalCurrency;
import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountingService;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.accounting.JournalEntry;
import com.cba.accounting.JournalEntryRepository;
import com.cba.currency.ExchangeRateService;
import com.cba.currency.dto.ConversionResult;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegulatoryReserveService — CBN regulatory risk reserve")
class RegulatoryReserveServiceTest {

    @Mock LoanRepository loanRepository;
    @Mock GlAccountingService gl;
    @Mock FunctionalCurrency functionalCurrency;
    @Mock ExchangeRateService exchangeRateService;
    @Mock JournalEntryRepository journalEntryRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks RegulatoryReserveService service;

    static final LocalDate MONTH_END = LocalDate.of(2026, 10, 31);
    final GlAccount retained = account("3001");
    final GlAccount reserve = account("3002");

    @BeforeEach
    void setUp() {
        when(functionalCurrency.get()).thenReturn("NGN");
        when(gl.activityAccount(FinancialActivity.EQUITY_RETAINED_EARNINGS)).thenReturn(retained);
        when(gl.activityAccount(FinancialActivity.EQUITY_REGULATORY_RISK_RESERVE)).thenReturn(reserve);
        when(gl.postJournal(any(), any(), any(), any(), any(), any())).thenReturn("GL-RRR");
    }

    @Test
    @DisplayName("nightly run on a day that isn't a month end does nothing")
    void notMonthEnd_skipped() {
        RegulatoryReserveService.ReserveRun run = service.post(LocalDate.of(2026, 10, 30), false);

        assertThat(run.skipped()).isTrue();
        verifyNoInteractions(loanRepository, jdbcTemplate);
        verify(gl, never()).postJournal(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("prudential above IFRS: the excess moves retained earnings → reserve (equity, no P&L)")
    void build() {
        totals(row("NGN", "300.00", "100.00"));
        reserveBalance("50.00");

        RegulatoryReserveService.ReserveRun run = service.post(MONTH_END, false);

        assertThat(run.position().target()).isEqualByComparingTo("200.00");
        assertThat(run.position().adjustment()).isEqualByComparingTo("150.00");
        List<JournalLine> lines = postedLines();
        assertThat(lines).containsExactly(
                JournalLine.debit(retained, new BigDecimal("150.0000"), "NGN"),
                JournalLine.credit(reserve, new BigDecimal("150.0000"), "NGN"));
        verify(gl).postJournal(any(), eq(MONTH_END), anyString(),
                eq(JournalEntry.EntityType.REGULATORY_RESERVE), isNull(), eq("RRR-" + MONTH_END));
    }

    @Test
    @DisplayName("prudential below IFRS: the reserve returns to retained earnings, as far as it was built")
    void release() {
        totals(row("NGN", "100.00", "400.00"));
        reserveBalance("80.00");

        RegulatoryReserveService.ReserveRun run = service.post(MONTH_END, false);

        assertThat(run.position().target()).isEqualByComparingTo("0");
        assertThat(postedLines()).containsExactly(
                JournalLine.debit(reserve, new BigDecimal("80.0000"), "NGN"),
                JournalLine.credit(retained, new BigDecimal("80.0000"), "NGN"));
    }

    @Test
    @DisplayName("foreign-currency loans count at the current rate; a manual run posts on any date")
    void foreignCurrency_manual() {
        totals(row("USD", "10.00", "4.00"));
        when(exchangeRateService.convert(new BigDecimal("10.00"), "USD", "NGN"))
                .thenReturn(new ConversionResult("USD", "NGN", new BigDecimal("10.00"), new BigDecimal("15000.00"), new BigDecimal("1500")));
        when(exchangeRateService.convert(new BigDecimal("4.00"), "USD", "NGN"))
                .thenReturn(new ConversionResult("USD", "NGN", new BigDecimal("4.00"), new BigDecimal("6000.00"), new BigDecimal("1500")));
        reserveBalance("0");

        RegulatoryReserveService.ReserveRun run = service.post(LocalDate.of(2026, 10, 12), true);

        assertThat(run.skipped()).isFalse();
        assertThat(run.position().adjustment()).isEqualByComparingTo("9000.00");
    }

    @Test
    @DisplayName("already at target: a rerun posts nothing")
    void rerun_noOp() {
        totals(row("NGN", "300.00", "100.00"));
        reserveBalance("200.00");

        RegulatoryReserveService.ReserveRun run = service.post(MONTH_END, false);

        assertThat(run.transactionId()).isNull();
        verify(gl, never()).postJournal(any(), any(), any(), any(), any(), any());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    private static Object[] row(String ccy, String prudential, String allowance) {
        return new Object[]{ccy, 3, 1L, new BigDecimal("1000.00"), new BigDecimal(allowance), new BigDecimal(prudential)};
    }

    private void totals(Object[]... rows) {
        List<Object[]> list = new ArrayList<>(List.of(rows));
        when(loanRepository.provisionTotals(any())).thenReturn(list);
    }

    private void reserveBalance(String amount) {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(reserve.getId()), eq("NGN")))
                .thenReturn(new BigDecimal(amount));
    }

    @SuppressWarnings("unchecked")
    private List<JournalLine> postedLines() {
        ArgumentCaptor<List<JournalLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(gl).postJournal(captor.capture(), any(), any(), any(), any(), any());
        return captor.getValue();
    }

    private static GlAccount account(String code) {
        GlAccount a = new GlAccount();
        a.setId(UUID.randomUUID());
        a.setGlCode(code);
        return a;
    }
}

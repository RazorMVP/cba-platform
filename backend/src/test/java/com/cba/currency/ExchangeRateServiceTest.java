package com.cba.currency;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.currency.dto.ConversionResult;
import com.cba.currency.dto.ExchangeRateRequest;
import com.cba.currency.dto.ExchangeRateResponse;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRateService — unit tests")
class ExchangeRateServiceTest {

    @Mock ExchangeRateRepository exchangeRateRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks ExchangeRateService service;

    private ExchangeRate buildRate(String from, String to, String rate) {
        ExchangeRate er = new ExchangeRate();
        er.setFromCurrency(from);
        er.setToCurrency(to);
        er.setRate(new BigDecimal(rate));
        er.setActive(true);
        return er;
    }

    @Test
    @DisplayName("setRate persists forward rate and auto-generates inverse rate")
    void setRate_persistsForwardAndInverse() {
        when(exchangeRateRepository.findByFromCurrencyAndToCurrency("USD", "KES"))
            .thenReturn(Optional.empty());
        when(exchangeRateRepository.findByFromCurrencyAndToCurrency("KES", "USD"))
            .thenReturn(Optional.empty());

        ExchangeRate saved = buildRate("USD", "KES", "135.50000000");
        saved = spy(saved);
        UUID fakeId = UUID.randomUUID();
        doReturn(fakeId).when(saved).getId();

        when(exchangeRateRepository.save(any())).thenReturn(saved);

        ExchangeRateRequest request = new ExchangeRateRequest("USD", "KES", new BigDecimal("135.50"));
        service.setRate(request);

        // save() called twice — once for forward rate, once for inverse
        verify(exchangeRateRepository, times(2)).save(any(ExchangeRate.class));
        verify(auditLogService).log(eq("EXCHANGE_RATE"), eq("USD/KES"), eq("SET"), isNull(), anyString());
    }

    @Test
    @DisplayName("getRate returns synthetic identity rate for same currency")
    void getRate_sameCurrency_returnsOne() {
        ExchangeRate result = service.getRate("USD", "USD");

        assertThat(result.getFromCurrency()).isEqualTo("USD");
        assertThat(result.getToCurrency()).isEqualTo("USD");
        assertThat(result.getRate()).isEqualByComparingTo(BigDecimal.ONE);
        verifyNoInteractions(exchangeRateRepository);
    }

    @Test
    @DisplayName("getRate returns stored active rate")
    void getRate_existingRate_returnsIt() {
        ExchangeRate rate = buildRate("USD", "KES", "135.50000000");
        when(exchangeRateRepository.findByFromCurrencyAndToCurrencyAndActiveTrue("USD", "KES"))
            .thenReturn(Optional.of(rate));

        ExchangeRate result = service.getRate("USD", "KES");

        assertThat(result.getRate()).isEqualByComparingTo(new BigDecimal("135.50000000"));
    }

    @Test
    @DisplayName("getRate throws EXCHANGE_RATE_NOT_CONFIGURED when no rate exists")
    void getRate_missingRate_throwsCbaException() {
        when(exchangeRateRepository.findByFromCurrencyAndToCurrencyAndActiveTrue("GBP", "NGN"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRate("GBP", "NGN"))
            .isInstanceOf(CbaException.class)
            .satisfies(ex -> assertThat(((CbaException) ex).getErrorCode())
                .isEqualTo("EXCHANGE_RATE_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("convert returns correct ConversionResult")
    void convert_returnsConversionResult() {
        ExchangeRate rate = buildRate("USD", "KES", "135.50000000");
        when(exchangeRateRepository.findByFromCurrencyAndToCurrencyAndActiveTrue("USD", "KES"))
            .thenReturn(Optional.of(rate));

        ConversionResult result = service.convert(new BigDecimal("100.00"), "USD", "KES");

        assertThat(result.fromCurrency()).isEqualTo("USD");
        assertThat(result.toCurrency()).isEqualTo("KES");
        assertThat(result.sourceAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        // 100 × 135.50 = 13550.0000
        assertThat(result.convertedAmount()).isEqualByComparingTo(new BigDecimal("13550.0000"));
        assertThat(result.rateUsed()).isEqualByComparingTo(new BigDecimal("135.50000000"));
    }

    @Test
    @DisplayName("convert same currency returns 1:1 (no rate lookup)")
    void convert_sameCurrency_oneToOne() {
        ConversionResult result = service.convert(new BigDecimal("500.00"), "KES", "KES");

        assertThat(result.convertedAmount()).isEqualByComparingTo(new BigDecimal("500.0000"));
        assertThat(result.rateUsed()).isEqualByComparingTo(BigDecimal.ONE);
        verifyNoInteractions(exchangeRateRepository);
    }

    @Test
    @DisplayName("getAllRates returns all active rates mapped to responses")
    void getAllRates_returnsMappedList() {
        ExchangeRate r1 = buildRate("USD", "KES", "135.50000000");
        ExchangeRate r2 = buildRate("KES", "USD", "0.00738000");
        when(exchangeRateRepository.findByActiveTrue()).thenReturn(List.of(r1, r2));

        List<ExchangeRateResponse> result = service.getAllRates();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).fromCurrency()).isEqualTo("USD");
        assertThat(result.get(1).fromCurrency()).isEqualTo("KES");
    }
}

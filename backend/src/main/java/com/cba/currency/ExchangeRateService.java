package com.cba.currency;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.currency.dto.ConversionResult;
import com.cba.currency.dto.ExchangeRateRequest;
import com.cba.currency.dto.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final AuditLogService auditLogService;

    private static final int CONVERSION_SCALE = 4;
    private static final int RATE_SCALE = 8;
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    /**
     * Upsert an exchange rate. If the pair already exists, updates the rate.
     * Also creates the inverse rate automatically (fromCurrency ↔ toCurrency).
     */
    @Transactional
    public ExchangeRateResponse setRate(ExchangeRateRequest request) {
        validateNotSameCurrency(request.fromCurrency(), request.toCurrency());

        String from = request.fromCurrency().toUpperCase();
        String to   = request.toCurrency().toUpperCase();

        // Upsert forward rate
        ExchangeRate forward = exchangeRateRepository
            .findByFromCurrencyAndToCurrency(from, to)
            .orElseGet(ExchangeRate::new);
        forward.setFromCurrency(from);
        forward.setToCurrency(to);
        forward.setRate(request.rate().setScale(RATE_SCALE, RoundingMode.HALF_UP));
        forward.setActive(true);
        forward.setCreatedBy(resolveActor());
        ExchangeRate saved = exchangeRateRepository.save(forward);

        // Automatically maintain the inverse rate
        BigDecimal inverseRate = BigDecimal.ONE.divide(request.rate(), RATE_SCALE, RoundingMode.HALF_UP);
        ExchangeRate inverse = exchangeRateRepository
            .findByFromCurrencyAndToCurrency(to, from)
            .orElseGet(ExchangeRate::new);
        inverse.setFromCurrency(to);
        inverse.setToCurrency(from);
        inverse.setRate(inverseRate);
        inverse.setActive(true);
        inverse.setCreatedBy(resolveActor());
        exchangeRateRepository.save(inverse);

        auditLogService.log("EXCHANGE_RATE", from + "/" + to, "SET",
            null, "rate=" + request.rate());
        log.info("Exchange rate set: 1 {} = {} {}", from, request.rate(), to);

        return toResponse(saved);
    }

    /**
     * Look up the active rate for a currency pair.
     * Throws if no rate is configured — the admin must set it first.
     */
    @Transactional(readOnly = true)
    public ExchangeRate getRate(String fromCurrency, String toCurrency) {
        String from = fromCurrency.toUpperCase();
        String to   = toCurrency.toUpperCase();

        if (from.equals(to)) {
            // Same currency — return synthetic rate of 1
            ExchangeRate identity = new ExchangeRate();
            identity.setFromCurrency(from);
            identity.setToCurrency(to);
            identity.setRate(BigDecimal.ONE);
            return identity;
        }

        return exchangeRateRepository
            .findByFromCurrencyAndToCurrencyAndActiveTrue(from, to)
            .orElseThrow(() -> CbaException.badRequest(
                "EXCHANGE_RATE_NOT_CONFIGURED",
                "No active exchange rate configured for " + from + " → " + to +
                ". An admin must set the rate before cross-currency transfers are allowed."
            ));
    }

    @Transactional(readOnly = true)
    public ExchangeRateResponse getRateResponse(String fromCurrency, String toCurrency) {
        return toResponse(getRate(fromCurrency, toCurrency));
    }

    /**
     * Convert an amount from one currency to another using the stored rate.
     * Returns the full ConversionResult including rate used (for audit).
     */
    @Transactional(readOnly = true)
    public ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        ExchangeRate rate = getRate(fromCurrency, toCurrency);
        BigDecimal converted = amount.multiply(rate.getRate(), MC)
            .setScale(CONVERSION_SCALE, RoundingMode.HALF_UP);

        return new ConversionResult(fromCurrency, toCurrency, amount, converted, rate.getRate());
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> getAllRates() {
        return exchangeRateRepository.findByActiveTrue().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public void deactivateRate(String fromCurrency, String toCurrency) {
        ExchangeRate rate = exchangeRateRepository
            .findByFromCurrencyAndToCurrencyAndActiveTrue(fromCurrency, toCurrency)
            .orElseThrow(() -> CbaException.notFound("ExchangeRate", fromCurrency + "/" + toCurrency));
        rate.setActive(false);
        exchangeRateRepository.save(rate);
        auditLogService.log("EXCHANGE_RATE", fromCurrency + "/" + toCurrency, "DEACTIVATED", null, null);
    }

    ExchangeRateResponse toResponse(ExchangeRate e) {
        BigDecimal inverseRate = e.getRate().compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : BigDecimal.ONE.divide(e.getRate(), RATE_SCALE, RoundingMode.HALF_UP);

        return new ExchangeRateResponse(
            e.getId(), e.getFromCurrency(), e.getToCurrency(),
            e.getRate(), inverseRate, e.isActive(), e.getUpdatedAt()
        );
    }

    private void validateNotSameCurrency(String from, String to) {
        if (from.equalsIgnoreCase(to)) {
            throw CbaException.badRequest("SAME_CURRENCY_RATE",
                "Cannot set an exchange rate between the same currency: " + from);
        }
    }

    private String resolveActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "system";
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String u = jwt.getClaimAsString("preferred_username");
            return u != null ? u : jwt.getSubject();
        }
        return auth.getName();
    }
}

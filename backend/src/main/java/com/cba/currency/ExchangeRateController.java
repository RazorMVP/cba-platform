package com.cba.currency;

import com.cba.common.response.ApiResponse;
import com.cba.currency.dto.ExchangeRateRequest;
import com.cba.currency.dto.ExchangeRateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exchange-rates")
@RequiredArgsConstructor
@Tag(name = "Exchange Rates", description = "Admin-managed currency exchange rate configuration")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set or update an exchange rate",
               description = "Upserts the rate for a currency pair. The inverse rate is automatically maintained.")
    public ResponseEntity<ApiResponse<ExchangeRateResponse>> setRate(
            @Valid @RequestBody ExchangeRateRequest request) {
        ExchangeRateResponse response = exchangeRateService.setRate(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "List all active exchange rates")
    public ResponseEntity<ApiResponse<List<ExchangeRateResponse>>> getAllRates() {
        return ResponseEntity.ok(ApiResponse.ok(exchangeRateService.getAllRates()));
    }

    @GetMapping("/{fromCurrency}/{toCurrency}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    @Operation(summary = "Get rate for a specific currency pair")
    public ResponseEntity<ApiResponse<ExchangeRateResponse>> getRate(
            @PathVariable String fromCurrency,
            @PathVariable String toCurrency) {
        return ResponseEntity.ok(ApiResponse.ok(
                exchangeRateService.getRateResponse(fromCurrency.toUpperCase(), toCurrency.toUpperCase())));
    }

    @DeleteMapping("/{fromCurrency}/{toCurrency}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate an exchange rate",
               description = "Marks the rate inactive. Cross-currency transfers using this pair will fail until a new rate is set.")
    public ResponseEntity<ApiResponse<Void>> deactivateRate(
            @PathVariable String fromCurrency,
            @PathVariable String toCurrency) {
        exchangeRateService.deactivateRate(fromCurrency.toUpperCase(), toCurrency.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}

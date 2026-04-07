package com.cba.accounting;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ManualJournalRequest(
        @NotNull LocalDate transactionDate,
        @NotBlank String currencyCode,
        String comments,
        @NotEmpty List<EntryLine> debits,
        @NotEmpty List<EntryLine> credits
) {
    public record EntryLine(
            @NotBlank String glCode,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String description
    ) {}
}

package com.cba.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ManualJournalRequest(
        @NotNull LocalDate transactionDate,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        String comments,
        @NotEmpty List<@Valid EntryLine> debits,
        @NotEmpty List<@Valid EntryLine> credits,
        /** Optional: stored on every line as its reference number. */
        String referenceNumber
) {
    public ManualJournalRequest(LocalDate transactionDate, String currencyCode, String comments,
                                List<EntryLine> debits, List<EntryLine> credits) {
        this(transactionDate, currencyCode, comments, debits, credits, null);
    }

    public record EntryLine(
            @NotBlank String glCode,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String description
    ) {}
}

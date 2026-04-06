package com.cba.account.dto;

import com.cba.account.AccountType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenAccountRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Account type is required")
        AccountType accountType,

        String currencyCode
) {}

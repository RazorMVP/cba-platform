package com.cba.product.dto;

import com.cba.product.DepositAccountType;
import com.cba.product.InterestCompounding;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record DepositProductRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,

        @NotNull
        DepositAccountType accountType,

        @Size(min = 3, max = 3)
        String currencyCode,

        @DecimalMin("0.00")
        BigDecimal minimumBalance,

        @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal interestRate,

        InterestCompounding interestCompounding
) {}

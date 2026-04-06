package com.cba.product.dto;

import com.cba.product.RepaymentType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record LoanProductRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,

        @Size(min = 3, max = 3)
        String currencyCode,

        @NotNull @DecimalMin("1.00")
        BigDecimal minPrincipal,

        @NotNull @DecimalMin("1.00")
        BigDecimal maxPrincipal,

        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal minInterestRate,

        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal maxInterestRate,

        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal defaultInterestRate,

        @NotNull @Min(1)
        Integer minTermMonths,

        @NotNull @Min(1)
        Integer maxTermMonths,

        RepaymentType repaymentType,

        BigDecimal originationFee,

        BigDecimal latePaymentFee
) {}

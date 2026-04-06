package com.cba.product.dto;

import com.cba.product.DepositAccountType;
import com.cba.product.DepositProduct;
import com.cba.product.InterestCompounding;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositProductResponse(
        UUID id,
        String name,
        String description,
        DepositAccountType accountType,
        String currencyCode,
        BigDecimal minimumBalance,
        BigDecimal interestRate,
        InterestCompounding interestCompounding,
        boolean active
) {
    public static DepositProductResponse from(DepositProduct p) {
        return new DepositProductResponse(
                p.getId(), p.getName(), p.getDescription(),
                p.getAccountType(), p.getCurrencyCode(),
                p.getMinimumBalance(), p.getInterestRate(),
                p.getInterestCompounding(), p.isActive()
        );
    }
}

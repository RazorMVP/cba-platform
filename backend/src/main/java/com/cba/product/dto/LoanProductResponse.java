package com.cba.product.dto;

import com.cba.product.LoanProduct;
import com.cba.product.RepaymentType;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanProductResponse(
        UUID id,
        String name,
        String description,
        String currencyCode,
        BigDecimal minPrincipal,
        BigDecimal maxPrincipal,
        BigDecimal minInterestRate,
        BigDecimal maxInterestRate,
        BigDecimal defaultInterestRate,
        int minTermMonths,
        int maxTermMonths,
        RepaymentType repaymentType,
        BigDecimal originationFee,
        BigDecimal latePaymentFee,
        boolean active
) {
    public static LoanProductResponse from(LoanProduct p) {
        return new LoanProductResponse(
                p.getId(), p.getName(), p.getDescription(), p.getCurrencyCode(),
                p.getMinPrincipal(), p.getMaxPrincipal(),
                p.getMinInterestRate(), p.getMaxInterestRate(), p.getDefaultInterestRate(),
                p.getMinTermMonths(), p.getMaxTermMonths(),
                p.getRepaymentType(), p.getOriginationFee(), p.getLatePaymentFee(),
                p.isActive()
        );
    }
}

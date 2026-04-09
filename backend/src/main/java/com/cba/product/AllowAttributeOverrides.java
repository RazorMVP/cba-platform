package com.cba.product;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Controls which product-level settings a loan officer may override per individual loan.
 * Stored as individual boolean columns on the loan_products table (prefix: allow_override_).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class AllowAttributeOverrides {

    private boolean amortizationType = true;

    private boolean interestType = true;

    private boolean repaymentEvery = true;

    private boolean repaymentFrequency = true;

    private boolean repaymentStrategy = true;

    private boolean graceOnPrincipalAndInterestPayment = true;

    private boolean graceOnInterestCharged = true;

    private boolean interestRatePerPeriod = true;
}

package com.cba.account.algorithm;

import com.cba.account.AccountNumberGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Wraps the existing {@link AccountNumberGenerator} as a named algorithm.
 * This is the fallback when no country-specific algorithm is configured for
 * a tenant's account type.
 *
 * <p>Format: {@code {branch_code}-{typeCode}-{7-digit-sequence}}
 * Example:   {@code 001-SAV-0001234}
 */
@Component
@RequiredArgsConstructor
public class MifosAccountNumberAlgorithm implements AccountNumberAlgorithm {

    private final AccountNumberGenerator accountNumberGenerator;

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.MIFOS;
    }

    @Override
    public String generate(AlgorithmContext ctx) {
        return accountNumberGenerator.generate(ctx.accountType());
    }

    /**
     * The Mifos format is internally generated — inbound Mifos-style numbers
     * cannot be validated by format alone (no check digit). This implementation
     * always returns {@link ValidationResult#skipped()} so that tenants without
     * a country algorithm do not have validation errors on existing numbers.
     */
    @Override
    public ValidationResult validate(String accountNumber, AlgorithmContext ctx) {
        return ValidationResult.skipped();
    }
}

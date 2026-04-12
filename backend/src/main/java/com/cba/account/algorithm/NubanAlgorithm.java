package com.cba.account.algorithm;

import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Nigerian Uniform Bank Account Number (NUBAN) algorithm.
 *
 * <p>Format: {@code BBBSSSSSSС} (10 digits)
 * <ul>
 *   <li>BBB — 3-digit CBN bank sort code (e.g. "058" for GTBank)</li>
 *   <li>SSSSSS — 6-digit sequential serial number (000001–999999)</li>
 *   <li>С — 1-digit check digit computed using CBN-specified weights</li>
 * </ul>
 *
 * <p>Check digit algorithm (CBN circular FPR/DIR/GEN/CIR/06/008):
 * <pre>
 *   weights  = {3, 7, 3, 3, 7, 3, 3, 7, 3}
 *   sum      = Σ(digit_i × weight_i)  for i = 0..8
 *   checkDigit = (10 − (sum mod 10)) mod 10
 * </pre>
 *
 * <p>PARANOID validation additionally ensures the first 3 digits match
 * the tenant's own bank code — used for intra-bank operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NubanAlgorithm implements AccountNumberAlgorithm {

    private static final int[] WEIGHTS         = {3, 7, 3, 3, 7, 3, 3, 7, 3};
    private static final long  MAX_SERIAL       = 999_999L;
    private static final int   NUBAN_LENGTH     = 10;
    private static final int   BANK_CODE_LENGTH = 3;
    private static final int   SERIAL_LENGTH    = 6;

    private final NubanSequenceRepository sequenceRepository;

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.NUBAN;
    }

    /**
     * Generates the next NUBAN for the given tenant and account type.
     * Uses {@link Propagation#REQUIRES_NEW} so the sequence increment is
     * committed regardless of whether the outer account-creation transaction
     * succeeds — preventing serial number reuse on rollback.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate(AlgorithmContext ctx) {
        String bankCode = ctx.bankCode();
        if (bankCode == null || bankCode.isBlank()) {
            throw CbaException.badRequest("NUBAN_BANK_CODE_MISSING",
                    "Tenant has no bank code configured for NUBAN generation. "
                  + "Set bankCode in tenant country_params.");
        }
        if (!bankCode.matches("\\d{3}")) {
            throw CbaException.badRequest("NUBAN_BANK_CODE_INVALID",
                    "Bank code must be exactly 3 digits, got: " + bankCode);
        }

        long serial     = nextSerial(ctx.tenantId(), ctx.accountType().name());
        String serialStr = String.format("%0" + SERIAL_LENGTH + "d", serial);
        String base      = bankCode + serialStr;                  // 9 digits
        int    checkDigit = computeCheckDigit(base);

        String nuban = base + checkDigit;
        log.debug("Generated NUBAN {} for tenant={} type={}", nuban, ctx.tenantId(), ctx.accountType());
        return nuban;
    }

    /**
     * Validates an account number string as a NUBAN.
     * <ul>
     *   <li>Always: must be exactly 10 digits; check digit must pass</li>
     *   <li>PARANOID mode: first 3 digits must match tenant's bank code</li>
     * </ul>
     */
    @Override
    public ValidationResult validate(String accountNumber, AlgorithmContext ctx) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return ValidationResult.fail("NUBAN_EMPTY", "Account number must not be empty");
        }

        String trimmed = accountNumber.trim();

        if (trimmed.length() != NUBAN_LENGTH) {
            return ValidationResult.fail("NUBAN_INVALID_LENGTH",
                    "NUBAN must be exactly " + NUBAN_LENGTH + " digits, got " + trimmed.length());
        }

        if (!trimmed.matches("\\d{10}")) {
            return ValidationResult.fail("NUBAN_NON_NUMERIC",
                    "NUBAN must contain only digits");
        }

        // Check digit validation
        String base            = trimmed.substring(0, 9);
        int    expectedCheck   = computeCheckDigit(base);
        int    actualCheck     = Character.getNumericValue(trimmed.charAt(9));

        if (expectedCheck != actualCheck) {
            return ValidationResult.fail("NUBAN_INVALID_CHECK_DIGIT",
                    "NUBAN check digit validation failed — the account number appears to be incorrect");
        }

        // PARANOID mode: also enforce own-bank code
        if (ctx.validationMode() == ValidationMode.PARANOID) {
            String bankCode = ctx.bankCode();
            if (bankCode == null || bankCode.isBlank()) {
                return ValidationResult.fail("NUBAN_BANK_CODE_MISSING",
                        "Cannot perform PARANOID validation: tenant has no bank code configured");
            }
            String inboundBankCode = trimmed.substring(0, BANK_CODE_LENGTH);
            if (!inboundBankCode.equals(bankCode)) {
                return ValidationResult.fail("NUBAN_BANK_CODE_MISMATCH",
                        "Account number does not belong to this bank (expected prefix "
                      + bankCode + ", got " + inboundBankCode + ")");
            }
        }

        return ValidationResult.ok();
    }

    // ── Private helpers ───────────────────────────────────────────────

    /**
     * CBN check digit formula:
     * <pre>sum = Σ(digit_i × weight_i);  checkDigit = (10 − (sum % 10)) % 10</pre>
     */
    int computeCheckDigit(String nineDigits) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(nineDigits.charAt(i)) * WEIGHTS[i];
        }
        return (10 - (sum % 10)) % 10;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected long nextSerial(UUID tenantId, String accountType) {
        NubanSequence seq = sequenceRepository
                .findByTenantAndTypeForUpdate(tenantId, accountType)
                .orElseGet(() -> {
                    // Auto-create sequence row if the seed migration was not run
                    NubanSequence s = new NubanSequence();
                    s.setId(new NubanSequence.NubanSequenceId(tenantId, accountType));
                    s.setLastSequence(0L);
                    return s;
                });

        long next = seq.getLastSequence() + 1;
        if (next > MAX_SERIAL) {
            throw CbaException.badRequest("NUBAN_SEQUENCE_EXHAUSTED",
                    "NUBAN serial number space exhausted for tenant " + tenantId
                  + " account type " + accountType
                  + ". Request an additional bank sort code from the CBN.");
        }

        seq.setLastSequence(next);
        sequenceRepository.save(seq);
        return next;
    }
}

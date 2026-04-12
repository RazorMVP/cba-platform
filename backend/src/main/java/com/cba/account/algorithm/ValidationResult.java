package com.cba.account.algorithm;

/**
 * Outcome of an account number validation check.
 *
 * @param valid     true when the account number passes all applicable checks
 * @param errorCode machine-readable error code (null when valid)
 * @param message   human-readable description of the failure (null when valid)
 */
public record ValidationResult(boolean valid, String errorCode, String message) {

    public static ValidationResult ok() {
        return new ValidationResult(true, null, null);
    }

    public static ValidationResult fail(String errorCode, String message) {
        return new ValidationResult(false, errorCode, message);
    }

    /** Convenience — skips validation for tenants with no algorithm configured. */
    public static ValidationResult skipped() {
        return new ValidationResult(true, null, null);
    }
}

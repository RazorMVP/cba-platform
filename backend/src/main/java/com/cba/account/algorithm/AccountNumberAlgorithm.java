package com.cba.account.algorithm;

/**
 * Pluggable strategy for generating and validating account numbers.
 *
 * <p>To add a new country algorithm:
 * <ol>
 *   <li>Add a constant to {@link AlgorithmType}</li>
 *   <li>Implement this interface as a Spring {@code @Component}</li>
 *   <li>Register BIN ranges / country params via the admin API</li>
 * </ol>
 * No changes to the framework are needed.
 */
public interface AccountNumberAlgorithm {

    /** Identifies this algorithm. */
    AlgorithmType getType();

    /**
     * Generates a unique account number for the given context.
     * Implementations must guarantee uniqueness within the tenant — typically
     * by atomically incrementing a sequence with a database-level lock.
     */
    String generate(AlgorithmContext ctx);

    /**
     * Validates an inbound account number.
     * Called on: account creation, payment destination, beneficiary registration.
     *
     * @param accountNumber the raw account number string supplied by the caller
     * @param ctx           tenant and account-type context
     * @return {@link ValidationResult#ok()} or a failure with an error code
     */
    ValidationResult validate(String accountNumber, AlgorithmContext ctx);
}

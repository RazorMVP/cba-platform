package com.cba.product;

/**
 * Account types for deposit products.
 * Defined in the product package to avoid a circular dependency with the account package.
 * AccountType in com.cba.account maps to this enum for account-level operations.
 */
public enum DepositAccountType {
    SAVINGS,
    CHECKING,
    FIXED_DEPOSIT
}

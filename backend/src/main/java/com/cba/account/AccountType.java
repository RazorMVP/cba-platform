package com.cba.account;

/**
 * Account type at the account level.
 * Mirrors com.cba.product.DepositAccountType — kept separate to avoid
 * a circular dependency between the account and product packages.
 */
public enum AccountType {
    SAVINGS,
    CHECKING,
    FIXED_DEPOSIT;

    public String typeCode() {
        return switch (this) {
            case SAVINGS -> "SAV";
            case CHECKING -> "CHK";
            case FIXED_DEPOSIT -> "FXD";
        };
    }
}

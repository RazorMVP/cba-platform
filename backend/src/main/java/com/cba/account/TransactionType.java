package com.cba.account;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_DEBIT,
    TRANSFER_CREDIT,
    LOAN_DISBURSEMENT,
    LOAN_REPAYMENT,
    INTEREST_CREDIT,
    FEE_CHARGE,
    REVERSAL,
    /** A card purchase cleared against its authorization hold. */
    CARD_PURCHASE,
    /** ATM cash (single-message 0200), debited at approval. */
    CARD_CASH_WITHDRAWAL,
    /** A completed card debit reversed back to the account. */
    CARD_REFUND,
    /** Money moved from the account to the customer's prepaid card wallet (backlog 15c). */
    PREPAID_CARD_LOAD,
    /** A prepaid card wallet balance redeemed back to the account. */
    PREPAID_CARD_UNLOAD
}

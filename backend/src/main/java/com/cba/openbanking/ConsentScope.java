package com.cba.openbanking;

/**
 * Canonical consent scope values for the CBA Open Banking platform.
 *
 * <p>Scopes are stored as plain strings in {@code consent_scopes} (via
 * {@code @ElementCollection}). This enum provides type-safe constants
 * so services can check {@code consent.getScopes().contains(ConsentScope.PAYMENTS.value())}
 * without hardcoding strings.
 *
 * <p>Scope catalogue:
 * <ul>
 *   <li>AISP (account information): {@code accounts_read}, {@code balances_read}, {@code transactions_read}</li>
 *   <li>PISP (payment initiation): {@code payments}</li>
 *   <li>CBPII (funds confirmation): {@code fundsconfirmation}</li>
 *   <li>Card extensions: {@code card_read}, {@code card_balances_read}, {@code card_transactions_read}</li>
 * </ul>
 */
public enum ConsentScope {

    // ── AISP ─────────────────────────────────────────────────────────────────
    ACCOUNTS_READ("accounts_read"),
    BALANCES_READ("balances_read"),
    TRANSACTIONS_READ("transactions_read"),

    // ── PISP ─────────────────────────────────────────────────────────────────
    PAYMENTS("payments"),

    // ── CBPII ────────────────────────────────────────────────────────────────
    FUNDS_CONFIRMATION("fundsconfirmation"),

    // ── Card extensions (card-service) ───────────────────────────────────────
    CARD_READ("card_read"),
    CARD_BALANCES_READ("card_balances_read"),
    CARD_TRANSACTIONS_READ("card_transactions_read");

    private final String value;

    ConsentScope(String value) {
        this.value = value;
    }

    /** The string as stored in the {@code consent_scopes} table. */
    public String value() {
        return value;
    }
}

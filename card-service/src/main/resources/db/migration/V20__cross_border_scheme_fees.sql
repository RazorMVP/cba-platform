-- ─────────────────────────────────────────────────────────────────────────────
-- V20 — Cross-border and currency-conversion scheme fees (backlog 15d, decision D2a)
-- ─────────────────────────────────────────────────────────────────────────────
-- CROSS_BORDER scheme fees apply when the acquirer's country (DE19) isn't the card's home country (its
-- BIN range); INTERNATIONAL_SERVICE fees when the transaction currency (DE49) isn't the card's currency
-- (DE51). Both are scheme charges to the issuer: they are part of settlement_items.scheme_fee_amount,
-- which the backend posts DR 5006 / CR 2300 — the split below is card-service's own record.
--
-- DE19 is stored as sent. Authorizations logged before this migration have none: their items count as
-- country unknown (no cross-border fee, flagged for reconciliation).
ALTER TABLE authorization_log
    ADD COLUMN IF NOT EXISTS acquiring_country VARCHAR(3);           -- DE19, ISO 3166 numeric; null = not sent

ALTER TABLE settlement_items
    ADD COLUMN IF NOT EXISTS cross_border_fee NUMERIC(19,4),         -- minor units, part of scheme_fee_amount
    ADD COLUMN IF NOT EXISTS currency_fee     NUMERIC(19,4),         -- minor units, part of scheme_fee_amount
    ADD COLUMN IF NOT EXISTS country_unknown  BOOLEAN NOT NULL DEFAULT false;

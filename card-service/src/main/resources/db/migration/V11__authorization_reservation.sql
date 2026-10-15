-- ─────────────────────────────────────────────────────────────────────────────
-- V11 — What an approval reserved, so a reversal can give it back
-- ─────────────────────────────────────────────────────────────────────────────
-- Approvals now reserve funds (they reserved nothing: any number of authorizations could
-- pass against the same balance). Debit: a backend account hold, keyed by hold_reference
-- (CARD-{cardId}-{stan}-{rrn}). Prepaid: the wallet is decremented by wallet_debit.
-- A reversal (0400) releases the hold or restores the wallet from these columns.
ALTER TABLE authorization_log
    ADD COLUMN IF NOT EXISTS hold_reference VARCHAR(64),
    ADD COLUMN IF NOT EXISTS wallet_debit   NUMERIC(19,4);

-- ─────────────────────────────────────────────────────────────────────────────
-- V62 — Card authorization holds: an idempotency key on account holds
-- ─────────────────────────────────────────────────────────────────────────────
-- A card approval now reserves the amount as an account hold, placed atomically with the
-- funds check (POST /api/v1/accounts/{id}/card-holds, card-service). Before this, approvals
-- reserved nothing: any number of authorizations could pass against the same balance.
-- external_reference is card-service's deterministic key (CARD-{cardId}-{stan}-{rrn}), so
-- a retried authorization finds its hold instead of placing a second one. The unique index
-- is full, not partial: a retry arriving after the hold was released must not re-hold.
ALTER TABLE account_holds ADD COLUMN IF NOT EXISTS external_reference VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_account_holds_external_ref
    ON account_holds (account_id, external_reference)
    WHERE external_reference IS NOT NULL;

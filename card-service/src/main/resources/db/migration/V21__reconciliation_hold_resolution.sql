-- ─────────────────────────────────────────────────────────────────────────────
-- V21 — Settlement reconciliation: resolving unreleased backend holds (backlog 15d, decision D3a)
-- ─────────────────────────────────────────────────────────────────────────────
-- A debit approval whose backend hold / 0200 debit call failed is declined RC 91 and released at once;
-- when even that release can't reach the backend, the reference stays on the declined row
-- (authorization_log.hold_reference) for reconciliation. These columns record that an admin retried the
-- release and the backend answered: RELEASED (hold given back), REFUNDED (a completed 0200 debit credited
-- back), NOT_FOUND_TOMBSTONED (the backend never saw it and now refuses it if it commits late) or
-- ALREADY_INACTIVE (released or expired before).
ALTER TABLE authorization_log
    ADD COLUMN IF NOT EXISTS hold_resolved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS hold_resolved_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS hold_resolution  VARCHAR(40);

-- The unresolved set the reconciliation view lists (small: only declines whose release never got through).
CREATE INDEX IF NOT EXISTS idx_auth_log_unresolved_holds
    ON authorization_log (created_at)
    WHERE response_code = '91' AND hold_reference IS NOT NULL AND hold_resolved_at IS NULL;

-- When batch close first claimed an item (PENDING → CLEARING): "claimed since" for an item whose capture
-- never got an answer. Items claimed before this migration have none (the view shows their created_at).
ALTER TABLE settlement_items
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_settlement_items_clearing
    ON settlement_items (batch_id)
    WHERE status = 'CLEARING';

CREATE INDEX IF NOT EXISTS idx_settlement_items_country_unknown
    ON settlement_items (batch_id)
    WHERE country_unknown;

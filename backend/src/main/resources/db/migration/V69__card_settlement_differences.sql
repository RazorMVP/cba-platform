-- ─────────────────────────────────────────────────────────────────────────────
-- V69 — Card scheme settlement differences within tolerance (backlog 15d, decision D7a)
-- ─────────────────────────────────────────────────────────────────────────────
-- A scheme's settlement report used to be recorded only when it equalled card-service's expected
-- net to the cent, so a one-cent rounding difference blocked the whole date. Within a configured
-- per-currency tolerance (app.cards.settlement.difference-tolerance, default 0) the report is now
-- recorded and the cash leg posts three lines:
--   2300 Card Scheme Settlement Payable at the EXPECTED net (what the bank owed the scheme),
--   1003 Card Scheme Settlement Account at the REPORTED net (what actually moved),
--   1004 Card Settlement Differences for the rest, on whichever side balances.
-- The difference is never netted into 2300 (IAS 1 §32: no offsetting). It is a claim on, or an
-- obligation to, the scheme pending investigation (IFRS 9 §3.1.1); operations clear 1004 by
-- manual journal — back to the scheme, or written off to profit or loss when it can't be
-- recovered (IFRS 9 §5.4.4). That is why 1004, unlike 1003 and 2300, accepts manual journals.
-- A debit balance is a claim on the scheme; a credit balance is owed to it.

-- 1. GL account, only where the V8 chart exists (like V63, V64).
INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, TRUE
FROM (VALUES
    ('30000000-0000-0000-0000-00000000000e', 'Card Settlement Differences', '1004', 'ASSET',
     '30000000-0000-0000-0000-000000000001',
     'Differences between a card scheme''s reported net settlement and the expected net, within tolerance, '
     || 'pending investigation (IFRS 9 §3.1.1). Cleared by manual journal; written off to P&L if unrecoverable (IFRS 9 §5.4.4).')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT 'ASSET_CARD_SETTLEMENT_DIFFERENCES', g.id
FROM gl_accounts g
WHERE g.id = '30000000-0000-0000-0000-00000000000e'::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 2. What card-service expected, and reported − expected (posted to 1004). Earlier rows matched exactly.
ALTER TABLE card_scheme_settlements
    ADD COLUMN IF NOT EXISTS expected_amount NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS difference      NUMERIC(19,4) NOT NULL DEFAULT 0;

UPDATE card_scheme_settlements SET expected_amount = amount WHERE expected_amount IS NULL;
ALTER TABLE card_scheme_settlements ALTER COLUMN expected_amount SET NOT NULL;

-- 3. The reported net may be zero when the expected net isn't (and the reverse) — only both zero
--    is nothing to post. The V64 inline CHECK has PostgreSQL's default name.
ALTER TABLE card_scheme_settlements DROP CONSTRAINT IF EXISTS card_scheme_settlements_amount_check;
ALTER TABLE card_scheme_settlements DROP CONSTRAINT IF EXISTS chk_card_scheme_settlement_amounts;
ALTER TABLE card_scheme_settlements ADD CONSTRAINT chk_card_scheme_settlement_amounts
    CHECK (amount <> 0 OR expected_amount <> 0);

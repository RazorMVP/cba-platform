-- ─────────────────────────────────────────────────────────────────────────────
-- V55 — Attribute each FX position equivalent line to the currency it values
-- ─────────────────────────────────────────────────────────────────────────────
-- The FX position (1200) is held in each foreign currency; its functional-currency
-- equivalent (1201) is one account in the functional currency. The revaluation job
-- retranslates each foreign currency's position at its own closing rate (IAS 21 §23),
-- so it needs the carrying amount per currency: position_currency tags every 1201
-- line with the currency whose position it values. Null on every other line.

ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS position_currency VARCHAR(3);

-- Backfill lines posted before this column existed. Each cross-currency leg posts a
-- position line and an equivalent line on opposite sides in the same journal, and the
-- two legs of one transfer always move in opposite directions, so the opposite-side
-- position line identifies the currency exactly.
UPDATE journal_entries eq
SET position_currency = pos.currency_code
FROM financial_activity_accounts fe, financial_activity_accounts fp, journal_entries pos
WHERE fe.financial_activity = 'ASSET_FX_POSITION_EQUIVALENT'
  AND fp.financial_activity = 'ASSET_FX_POSITION'
  AND eq.gl_account_id = fe.gl_account_id
  AND pos.gl_account_id = fp.gl_account_id
  AND pos.transaction_id = eq.transaction_id
  AND pos.entry_type <> eq.entry_type
  AND eq.position_currency IS NULL;

-- A single-line reversal carries its original's currency.
UPDATE journal_entries rev
SET position_currency = orig.position_currency
FROM journal_entries orig
WHERE rev.reversal_id = orig.id
  AND orig.position_currency IS NOT NULL
  AND rev.position_currency IS NULL;

CREATE INDEX IF NOT EXISTS idx_je_position_currency
    ON journal_entries (gl_account_id, position_currency)
    WHERE position_currency IS NOT NULL;

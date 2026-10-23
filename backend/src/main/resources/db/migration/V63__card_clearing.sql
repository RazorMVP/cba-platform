-- ─────────────────────────────────────────────────────────────────────────────
-- V63 — Card clearing posts to the GL (backlog 15a)
-- ─────────────────────────────────────────────────────────────────────────────
-- A card authorization only reserves funds (a hold, no journal: nothing is owed to
-- anyone yet). When the transaction is presented for clearing the bank becomes liable
-- to the card scheme for it on the cardholder's behalf: that part of the customer's
-- deposit is extinguished (IFRS 9 §3.3.1) and a payable to the scheme is recognised
-- (a contractual obligation to deliver cash, IAS 32 §11). So clearing posts
--   DR customer deposit (savings control, or overdraft portfolio) / CR 2300.
-- 2300 is cleared when the scheme settles (backlog 15b: interchange, scheme fees and
-- the settlement-bank leg). A refund (reversal of a completed card debit) posts the
-- opposite.
--
-- 1. GL account, only where the V8 chart exists (like V54, V56–V60).

INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, FALSE
FROM (VALUES
    ('30000000-0000-0000-0000-000000000014', 'Card Scheme Settlement Payable', '2300', 'LIABILITY',
     '30000000-0000-0000-0000-000000000010',
     'Cleared card transactions owed to the card schemes until they settle (IFRS 9 §3.3.1; IAS 32 §11).')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES
    ('LIABILITY_CARD_SETTLEMENT', '30000000-0000-0000-0000-000000000014')
) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 2. A card hold can now end as CAPTURED (cleared: the account was debited) or REFUNDED
--    (a completed card debit reversed: the account was credited back).
ALTER TABLE account_holds DROP CONSTRAINT IF EXISTS chk_hold_status;
ALTER TABLE account_holds ADD CONSTRAINT chk_hold_status
    CHECK (status IN ('ACTIVE','RELEASED','EXPIRED','CAPTURED','REFUNDED'));

ALTER TABLE account_holds
    ADD COLUMN IF NOT EXISTS captured_amount NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS captured_at     TIMESTAMPTZ,
    -- A completed card debit is refunded once, for at most what was captured (a partial
    -- reversal refunds only its amount).
    ADD COLUMN IF NOT EXISTS refunded_amount NUMERIC(19,4),
    -- The reversal that refunded it: only a retry of that reversal counts as done.
    ADD COLUMN IF NOT EXISTS refund_reference VARCHAR(64);

-- ─────────────────────────────────────────────────────────────────────────────
-- V68 — Prepaid card wallets post to the GL (backlog 15c)
-- ─────────────────────────────────────────────────────────────────────────────
-- A prepaid wallet balance is money on deposit with the issuer but not in a current or
-- savings account (CBN Guidelines on Stored Value/Prepaid Card Issuance and Operations,
-- 2010, revised 2012), and the holder can have it refunded in cash (2012 §3.7): a financial
-- liability (IAS 32 §11) at amortised cost (IFRS 9 §4.2.1), kept apart from savings control.
--   load:      DR customer deposit / CR 2500 Prepaid Card Liabilities
--   unload:    DR 2500 / CR customer deposit
--   clearing:  DR 2500 / CR 2300 Card Scheme Settlement Payable (+ interchange / scheme fees, as 15b)
--   refund:    the opposite of the clearing, pro rata
-- No breakage income is recognised (IFRS 15 §5(c) excludes IFRS 9 instruments), and no fee is
-- taken from the balance (the CBN guidelines forbid it).

INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, FALSE
FROM (VALUES
    ('30000000-0000-0000-0000-000000000016', 'Prepaid Card Liabilities', '2500', 'LIABILITY',
     '30000000-0000-0000-0000-000000000010',
     'Prepaid card wallet balances owed to cardholders (CBN stored-value guidelines; IAS 32 §11; IFRS 9 §4.2.1).')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES ('LIABILITY_PREPAID_CARD', '30000000-0000-0000-0000-000000000016')) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- Every prepaid movement card-service asks the backend to post, idempotent on its reference.
CREATE TABLE IF NOT EXISTS prepaid_card_movements (
    id                     UUID PRIMARY KEY,
    reference              VARCHAR(80)   NOT NULL UNIQUE,
    movement_type          VARCHAR(10)   NOT NULL CHECK (movement_type IN ('LOAD','UNLOAD','CLEARING','REFUND')),
    customer_id            UUID,
    card_id                UUID,
    account_id             UUID,
    amount                 NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency_code          VARCHAR(3)    NOT NULL,
    scheme                 VARCHAR(20),
    interchange_amount     NUMERIC(19,4),
    interchange_direction  VARCHAR(10),
    scheme_fee_amount      NUMERIC(19,4),
    -- A CLEARING row: what its one refund gave back. A REFUND row: the clearing it reverses.
    refunded_amount        NUMERIC(19,4),
    refund_reference       VARCHAR(80),
    original_reference     VARCHAR(80),
    journal_transaction_id VARCHAR(64)   NOT NULL,
    created_by             VARCHAR(100),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_prepaid_movements_card ON prepaid_card_movements (card_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- V64 — Card interchange income, scheme fees and the scheme cash leg (backlog 15b)
-- ─────────────────────────────────────────────────────────────────────────────
-- As card issuer the bank provides the card service to its cardholder: it issues the card,
-- authorizes and funds each transaction and bears the fraud and credit risk, so it is the
-- principal (IFRS 15 B35, B37). Interchange it earns on a purchase is revenue, recognised when
-- the transaction clears (IFRS 15 §38). The card scheme is a supplier, not a customer, so its
-- fees are an expense, not a reduction of revenue (IFRS 15 §70 does not apply), and the two
-- are shown gross (IAS 1 §32). On ATM cash at another bank's machine the issuer pays
-- interchange to the acquirer: an expense.
--
-- At clearing, in the same journal as the customer debit (GL 2300 is one net payable per
-- scheme — the scheme settles each day's net in one payment, IAS 32 §42):
--   purchase:  DR 2300 / CR 4005 interchange earned;   DR 5006 / CR 2300 scheme fees
--   ATM cash:  DR 5007 / CR 2300 interchange paid;     DR 5006 / CR 2300 scheme fees
-- A refund of a cleared debit reverses them in proportion to the amount refunded.
-- When operations record the scheme's settlement report and it equals card-service's
-- expected net, the cash leg posts DR 2300 / CR 1003 (or the reverse when the scheme owes
-- the bank).
--
-- 1. GL accounts, only where the V8 chart exists (like V54, V56–V60, V63).

INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, FALSE
FROM (VALUES
    ('30000000-0000-0000-0000-00000000000d', 'Card Scheme Settlement Account', '1003', 'ASSET',
     '30000000-0000-0000-0000-000000000001',
     'The bank''s account at its card settlement bank: card scheme net settlements are paid from and received into it.'),
    ('30000000-0000-0000-0000-000000000035', 'Card Interchange Income', '4005', 'INCOME',
     '30000000-0000-0000-0000-000000000030',
     'Interchange earned as card issuer on cleared purchases (IFRS 15 B35, B37, §38).'),
    ('30000000-0000-0000-0000-000000000046', 'Card Scheme Fees', '5006', 'EXPENSE',
     '30000000-0000-0000-0000-000000000040',
     'Assessment and network fees charged by the card schemes (a supplier cost, shown gross — IAS 1 §32).'),
    ('30000000-0000-0000-0000-000000000047', 'Card Interchange Expense', '5007', 'EXPENSE',
     '30000000-0000-0000-0000-000000000040',
     'Interchange the bank pays to acquirers on its cardholders'' cash withdrawals at other banks'' ATMs.')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES
    ('ASSET_CARD_SCHEME_SETTLEMENT', '30000000-0000-0000-0000-00000000000d'),
    ('INCOME_CARD_INTERCHANGE',      '30000000-0000-0000-0000-000000000035'),
    ('EXPENSE_CARD_SCHEME_FEES',     '30000000-0000-0000-0000-000000000046'),
    ('EXPENSE_CARD_INTERCHANGE',     '30000000-0000-0000-0000-000000000047')
) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 2. What a card debit posted besides the amount (major units of the account currency), so an
--    idempotent repeat reports the same figures and a refund reverses them in proportion.
ALTER TABLE account_holds
    ADD COLUMN IF NOT EXISTS card_scheme           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS interchange_amount    NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS interchange_direction VARCHAR(6),
    ADD COLUMN IF NOT EXISTS scheme_fee_amount     NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS interchange_reversed  NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS scheme_fee_reversed   NUMERIC(19,4);

ALTER TABLE account_holds DROP CONSTRAINT IF EXISTS chk_hold_interchange_direction;
ALTER TABLE account_holds ADD CONSTRAINT chk_hold_interchange_direction
    CHECK (interchange_direction IS NULL OR interchange_direction IN ('EARNED', 'PAID'));

-- 3. Card scheme net settlements (the cash leg). card-service's reference makes a repeated
--    request post nothing: journals themselves carry no idempotency.
CREATE TABLE IF NOT EXISTS card_scheme_settlements (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    reference              VARCHAR(64)   NOT NULL UNIQUE,
    scheme                 VARCHAR(20)   NOT NULL,
    settlement_date        DATE          NOT NULL,
    currency_code          VARCHAR(3)    NOT NULL,
    -- Signed, major units: positive = the bank paid the scheme (DR 2300 / CR 1003).
    amount                 NUMERIC(19,4) NOT NULL CHECK (amount <> 0),
    journal_transaction_id VARCHAR(64)   NOT NULL,
    created_by             VARCHAR(100),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_card_scheme_settlements_date
    ON card_scheme_settlements (settlement_date, scheme);
